package com.example.DragDown.Repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("redis-integration")
@Testcontainers
class RedisRoomRepositoryConcurrencyTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private RedisRoomRepository repository;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        repository = new RedisRoomRepository(
                redisTemplate,
                redisScript("scripts/create_room.lua"),
                redisScript("scripts/join_room.lua"),
                redisScript("scripts/leave_room.lua")
        );
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void onlyOnePlayerCanTakeTheLastAvailableSeat() throws Exception {
        String roomId = "room-last-seat";
        createRoomWithHost(roomId, "host", 2);

        int contenderCount = 20;
        List<Long> results = runConcurrently(contenderCount, index ->
                repository.tryJoinRoomAtomically(roomId, "player-" + index, "127.0.0.1", 2000 + index)
        );

        assertThat(results).filteredOn(result -> result == 3L).hasSize(1);
        assertThat(results).filteredOn(result -> result == 1L).hasSize(contenderCount - 1);
        assertThat(repository.getRoomPlayerCount(roomId)).isEqualTo(2L);
    }

    @Test
    void samePlayerCanJoinOnlyOneOfTwoRoomsConcurrently() throws Exception {
        String firstRoomId = "room-first";
        String secondRoomId = "room-second";
        String username = "same-player";
        createRoomWithHost(firstRoomId, "first-host", 4);
        createRoomWithHost(secondRoomId, "second-host", 4);

        List<Long> results = runConcurrently(2, index -> repository.tryJoinRoomAtomically(
                index == 0 ? firstRoomId : secondRoomId,
                username,
                "127.0.0.1",
                3000 + index
        ));

        assertThat(results).filteredOn(result -> result == 3L).hasSize(1);
        assertThat(results).filteredOn(result -> result == 6L).hasSize(1);

        String joinedRoomId = repository.findRoomIdByPlayer(username).orElseThrow();
        assertThat(joinedRoomId).isIn(firstRoomId, secondRoomId);
        assertThat(repository.getRoomPlayers(joinedRoomId)).contains(username);

        String otherRoomId = joinedRoomId.equals(firstRoomId) ? secondRoomId : firstRoomId;
        assertThat(repository.getRoomPlayers(otherRoomId)).doesNotContain(username);
    }

    private void createRoomWithHost(String roomId, String hostUsername, int maxPlayers) {
        repository.saveNewRoom(roomId, roomId, hostUsername, "127.0.0.1", maxPlayers);
        repository.addPlayerToRoom(roomId, hostUsername);
        repository.setPlayerLocation(hostUsername, roomId);
        repository.savePlayerEndpoint(hostUsername, "127.0.0.1", 7777);
        repository.addRoomToActiveList(roomId);
    }

    private List<Long> runConcurrently(int taskCount, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                Callable<Long> callable = () -> {
                    ready.countDown();
                    start.await();
                    return task.run(taskIndex);
                };
                futures.add(executor.submit(callable));
            }

            ready.await();
            start.countDown();

            List<Long> results = new ArrayList<>();
            for (Future<Long> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private RedisScript<Long> redisScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    @FunctionalInterface
    private interface IndexedTask {
        long run(int index);
    }
}
