package com.example.DragDown.Repository;

import com.example.DragDown.Exception.RoomException;
import com.example.DragDown.Service.MatchRoomService;
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
class RedisRoomClosureConcurrencyTest {

    private static final int REPEAT_COUNT = 10;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private RedisRoomRepository repository;
    private MatchRoomService service;

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
        service = new MatchRoomService(repository);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void hostLeaveAndConcurrentJoinsAlwaysRemoveRoomAndPlayerState() throws Exception {
        for (int round = 0; round < REPEAT_COUNT; round++) {
            String roomId = "room-close-join-" + round;
            String host = "host-join-" + round;
            createRoomWithHost(roomId, host, 4);

            List<String> players = new ArrayList<>();
            players.add(host);
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                service.leaveRoom(host);
                return null;
            });

            for (int index = 0; index < 8; index++) {
                String username = "joiner-" + round + "-" + index;
                int port = 3000 + index;
                players.add(username);
                tasks.add(() -> {
                    try {
                        service.joinRoom(username, "127.0.0.1", roomId, port);
                    } catch (RoomException expectedWhenRoomCloses) {
                        assertThat(expectedWhenRoomCloses.getMessage()).satisfiesAnyOf(
                                message -> assertThat(message).contains("방이 꽉 찼습니다"),
                                message -> assertThat(message).contains("찾을 수 없습니다"),
                                message -> assertThat(message).contains("입장 처리 중 방이 종료되었습니다")
                        );
                    }
                    return null;
                });
            }

            runConcurrently(tasks);
            assertClosedRoomState(roomId, players);
        }
    }

    @Test
    void hostLeaveAndConcurrentGuestLeavesAreIdempotent() throws Exception {
        for (int round = 0; round < REPEAT_COUNT; round++) {
            String roomId = "room-close-leave-" + round;
            String host = "host-leave-" + round;
            List<String> players = new ArrayList<>();
            players.add(host);
            createRoomWithHost(roomId, host, 4);

            for (int index = 0; index < 3; index++) {
                String username = "guest-leave-" + round + "-" + index;
                players.add(username);
                service.joinRoom(username, "127.0.0.1", roomId, 4000 + index);
            }

            List<Callable<Void>> tasks = new ArrayList<>();
            for (String username : players) {
                tasks.add(() -> {
                    service.leaveRoom(username);
                    return null;
                });
            }

            runConcurrently(tasks);
            assertClosedRoomState(roomId, players);
        }
    }

    @Test
    void duplicateConcurrentHostLeavesAreIdempotent() throws Exception {
        for (int round = 0; round < REPEAT_COUNT; round++) {
            String roomId = "room-double-host-leave-" + round;
            String host = "double-host-" + round;
            createRoomWithHost(roomId, host, 4);

            List<Callable<Void>> tasks = List.of(
                    () -> {
                        service.leaveRoom(host);
                        return null;
                    },
                    () -> {
                        service.leaveRoom(host);
                        return null;
                    }
            );

            runConcurrently(tasks);
            assertClosedRoomState(roomId, List.of(host));
        }
    }

    private void createRoomWithHost(String roomId, String hostUsername, int maxPlayers) {
        repository.saveNewRoom(roomId, roomId, hostUsername, "127.0.0.1", maxPlayers);
        repository.addPlayerToRoom(roomId, hostUsername);
        repository.setPlayerLocation(hostUsername, roomId);
        repository.savePlayerEndpoint(hostUsername, "127.0.0.1", 7777);
        repository.addRoomToActiveList(roomId);
    }

    private void assertClosedRoomState(String roomId, List<String> players) {
        assertThat(repository.getRoomDetailsMap(roomId)).isEmpty();
        assertThat(repository.getRoomPlayers(roomId)).isEmpty();
        assertThat(repository.getActiveRoomIds()).doesNotContain(roomId);

        for (String player : players) {
            assertThat(repository.findRoomIdByPlayer(player)).isEmpty();
            assertThat(repository.getPlayerEndpoints(List.of(player)).get(player))
                    .isEqualTo("Endpoint 정보 없음");
        }
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }

            ready.await();
            start.countDown();

            for (Future<Void> future : futures) {
                future.get();
            }
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
}
