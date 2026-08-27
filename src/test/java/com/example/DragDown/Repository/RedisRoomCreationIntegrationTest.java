package com.example.DragDown.Repository;

import com.example.DragDown.Dto.MatchDto.RoomDetails;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("redis-integration")
@Testcontainers
class RedisRoomCreationIntegrationTest {

    private static final String FIRST_CANDIDATE = "room-create-first";
    private static final String SECOND_CANDIDATE = "room-create-second";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private CoordinatedRedisRoomRepository repository;
    private MatchRoomService service;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        repository = new CoordinatedRedisRoomRepository(
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
    void sameUserConcurrentRoomCreationAllowsExactlyOneCompleteRoom() throws Exception {
        String username = "concurrent-host";
        List<Callable<CreationAttempt>> tasks = List.of(
                () -> createAttempt(username, "First room", "127.0.0.11", 5101),
                () -> createAttempt(username, "Second room", "127.0.0.12", 5102)
        );

        List<CreationAttempt> attempts = runConcurrently(tasks);
        List<CreationAttempt> successes = attempts.stream().filter(CreationAttempt::succeeded).toList();
        List<CreationAttempt> failures = attempts.stream().filter(attempt -> !attempt.succeeded()).toList();

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst().failure()).isInstanceOf(RoomException.class);

        RoomDetails createdRoom = successes.getFirst().roomDetails();
        String successfulRoomId = createdRoom.getRoomId();
        String rejectedRoomId = successfulRoomId.equals(FIRST_CANDIDATE) ? SECOND_CANDIDATE : FIRST_CANDIDATE;

        assertThat(repository.findRoomIdByPlayer(username)).contains(successfulRoomId);
        assertThat(repository.getRoomPlayers(successfulRoomId)).containsExactly(username);
        assertThat(repository.getActiveRoomIds()).containsExactly(successfulRoomId);
        assertThat(repository.getPlayerEndpoints(List.of(username)).get(username))
                .isEqualTo(createdRoom.getPlayerIps().get(username));

        assertThat(repository.getRoomDetailsMap(rejectedRoomId)).isEmpty();
        assertThat(repository.getRoomPlayers(rejectedRoomId)).isEmpty();
        assertThat(repository.getActiveRoomIds()).doesNotContain(rejectedRoomId);
    }

    @Test
    void normalCreationStoresAllRoomAndHostState() {
        String roomId = "room-normal-create";
        FixedCandidateRedisRoomRepository fixedRepository = fixedRepository(roomId);
        MatchRoomService fixedService = new MatchRoomService(fixedRepository);

        RoomDetails createdRoom = fixedService.createRoom(
                "normal-host",
                "Normal room",
                4,
                "127.0.0.21",
                6101
        );

        assertThat(createdRoom.getRoomId()).isEqualTo(roomId);
        assertThat(fixedRepository.getRoomDetailsMap(roomId))
                .containsEntry("name", "Normal room")
                .containsEntry("hostUsername", "normal-host")
                .containsEntry("hostIp", "127.0.0.21")
                .containsEntry("maxPlayers", "4")
                .containsEntry("state", "waiting")
                .containsKey("createdAt");
        assertThat(fixedRepository.getRoomPlayers(roomId)).containsExactly("normal-host");
        assertThat(fixedRepository.getActiveRoomIds()).containsExactly(roomId);
        assertThat(fixedRepository.findRoomIdByPlayer("normal-host")).contains(roomId);
        assertThat(fixedRepository.getPlayerEndpoints(List.of("normal-host")).get("normal-host"))
                .isEqualTo("127.0.0.21:6101");
    }

    @Test
    void creationByUserAlreadyInRoomLeavesCandidateEmpty() {
        String existingRoomId = "room-existing-membership";
        String rejectedCandidateId = "room-rejected-candidate";
        FixedCandidateRedisRoomRepository fixedRepository = fixedRepository(rejectedCandidateId);
        createRoomWithHost(fixedRepository, existingRoomId, "existing-host", "127.0.0.22", 6201);
        MatchRoomService fixedService = new MatchRoomService(fixedRepository);

        assertThatThrownBy(() -> fixedService.createRoom(
                "existing-host",
                "Rejected room",
                4,
                "127.0.0.23",
                6202
        ))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining(existingRoomId);

        assertThat(fixedRepository.findRoomIdByPlayer("existing-host")).contains(existingRoomId);
        assertThat(fixedRepository.getRoomDetailsMap(rejectedCandidateId)).isEmpty();
        assertThat(fixedRepository.getRoomPlayers(rejectedCandidateId)).isEmpty();
        assertThat(fixedRepository.getActiveRoomIds()).doesNotContain(rejectedCandidateId);
    }

    @Test
    void roomIdCollisionDoesNotOverwriteExistingRoomAndRetriesWithNewCandidate() {
        String collidingRoomId = "room-collision";
        String createdRoomId = "room-after-collision";
        FixedCandidateRedisRoomRepository fixedRepository = fixedRepository(collidingRoomId, createdRoomId);
        createRoomWithHost(fixedRepository, collidingRoomId, "original-host", "127.0.0.24", 6301);
        var originalDetails = fixedRepository.getRoomDetailsMap(collidingRoomId);
        var originalPlayers = fixedRepository.getRoomPlayers(collidingRoomId);
        MatchRoomService fixedService = new MatchRoomService(fixedRepository);

        RoomDetails createdRoom = fixedService.createRoom(
                "new-host",
                "New room",
                3,
                "127.0.0.25",
                6302
        );

        assertThat(createdRoom.getRoomId()).isEqualTo(createdRoomId);
        assertThat(fixedRepository.getRoomDetailsMap(collidingRoomId)).isEqualTo(originalDetails);
        assertThat(fixedRepository.getRoomPlayers(collidingRoomId)).isEqualTo(originalPlayers);
        assertThat(fixedRepository.getActiveRoomIds()).contains(collidingRoomId, createdRoomId);
        assertThat(fixedRepository.findRoomIdByPlayer("original-host")).contains(collidingRoomId);
        assertThat(fixedRepository.findRoomIdByPlayer("new-host")).contains(createdRoomId);
    }

    @Test
    void orphanPlayersKeyIsTreatedAsCollisionWithoutChangingIt() {
        String orphanRoomId = "room-orphan-players";
        String createdRoomId = "room-after-orphan";
        String orphanPlayersKey = playersKey(orphanRoomId);
        redisTemplate.opsForSet().add(orphanPlayersKey, "orphan-player");
        byte[] originalOrphanPlayers = redisTemplate.dump(orphanPlayersKey);

        FixedCandidateRedisRoomRepository fixedRepository = fixedRepository(orphanRoomId, createdRoomId);
        RoomDetails createdRoom = new MatchRoomService(fixedRepository).createRoom(
                "new-orphan-host", "After orphan", 4, "127.0.0.26", 6303);

        assertThat(createdRoom.getRoomId()).isEqualTo(createdRoomId);
        assertThat(redisTemplate.dump(orphanPlayersKey)).isEqualTo(originalOrphanPlayers);
        assertThat(redisTemplate.hasKey(detailsKey(orphanRoomId))).isFalse();
        assertThat(fixedRepository.getRoomPlayers(orphanRoomId)).containsExactly("orphan-player");
        assertThat(fixedRepository.getActiveRoomIds()).doesNotContain(orphanRoomId);
    }

    @Test
    void malformedRedisKeyTypesRejectCreationWithoutAnyWrite() {
        List<String> malformedKeys = List.of(
                detailsKey("room-malformed"),
                playersKey("room-malformed"),
                "rooms:active_set",
                "player:locations",
                "player:ips"
        );

        for (String malformedKey : malformedKeys) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
            redisTemplate.opsForValue().set(malformedKey, "wrong-type");
            Map<String, byte[]> before = dumpCreationKeys("room-malformed");
            FixedCandidateRedisRoomRepository fixedRepository = fixedRepository("room-malformed");

            assertThatThrownBy(() -> new MatchRoomService(fixedRepository).createRoom(
                    "malformed-host", "Malformed room", 4, "127.0.0.27", 6304))
                    .isInstanceOf(RoomException.class)
                    .hasMessageContaining("Redis 상태");

            assertThat(dumpCreationKeys("room-malformed"))
                    .usingRecursiveComparison()
                    .isEqualTo(before);
        }
    }

    @Test
    void completeStateIsAcceptedWhenCreateScriptResponseIsUnavailable() {
        AmbiguousResultRedisRoomRepository ambiguousRepository = ambiguousRepository(
                "room-response-lost", AmbiguousCreateMode.COMPLETE);

        RoomDetails createdRoom = new MatchRoomService(ambiguousRepository).createRoom(
                "response-lost-host", "Response lost", 4, "127.0.0.28", 6305);

        assertThat(createdRoom.getRoomId()).isEqualTo("room-response-lost");
        assertThat(ambiguousRepository.findRoomIdByPlayer("response-lost-host"))
                .contains("room-response-lost");
        assertThat(ambiguousRepository.getPlayerEndpoints(List.of("response-lost-host"))
                .get("response-lost-host")).isEqualTo("127.0.0.28:6305");
    }

    @Test
    void completeStateWithConcurrentGuestIsAcceptedWhenCreateScriptResponseIsUnavailable() {
        String roomId = "room-response-lost-after-join";
        AmbiguousResultRedisRoomRepository ambiguousRepository = ambiguousRepository(
                roomId, AmbiguousCreateMode.COMPLETE_WITH_GUEST);

        RoomDetails createdRoom = new MatchRoomService(ambiguousRepository).createRoom(
                "joined-response-host", "Response lost after join", 4, "127.0.0.31", 6308);

        assertThat(createdRoom.getRoomId()).isEqualTo(roomId);
        assertThat(createdRoom.getPlayers()).containsExactlyInAnyOrder(
                "joined-response-host", "response-lost-guest");
        assertThat(ambiguousRepository.getRoomPlayers(roomId)).containsExactlyInAnyOrder(
                "joined-response-host", "response-lost-guest");
        assertThat(ambiguousRepository.findRoomIdByPlayer("joined-response-host")).contains(roomId);
        assertThat(ambiguousRepository.findRoomIdByPlayer("response-lost-guest")).contains(roomId);
    }

    @Test
    void absentStateIsReportedAsFailedWhenCreateScriptResponseIsUnavailable() {
        AmbiguousResultRedisRoomRepository ambiguousRepository = ambiguousRepository(
                "room-no-create-state", AmbiguousCreateMode.ABSENT);

        assertThatThrownBy(() -> new MatchRoomService(ambiguousRepository).createRoom(
                "absent-host", "Absent result", 4, "127.0.0.29", 6306))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("결과를 확인할 수 없습니다");

        assertThat(dumpCreationKeys("room-no-create-state").values()).containsOnlyNulls();
    }

    @Test
    void partialStateIsReportedAsInconsistentWhenCreateScriptResponseIsUnavailable() {
        AmbiguousResultRedisRoomRepository ambiguousRepository = ambiguousRepository(
                "room-partial-create-state", AmbiguousCreateMode.PARTIAL);

        assertThatThrownBy(() -> new MatchRoomService(ambiguousRepository).createRoom(
                "partial-host", "Partial result", 4, "127.0.0.30", 6307))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("일관되지 않습니다");

        assertThat(ambiguousRepository.getRoomDetailsMap("room-partial-create-state")).isNotEmpty();
        assertThat(ambiguousRepository.getRoomPlayers("room-partial-create-state")).isEmpty();
        assertThat(ambiguousRepository.getActiveRoomIds()).isEmpty();
        assertThat(ambiguousRepository.findRoomIdByPlayer("partial-host")).isEmpty();
    }

    private CreationAttempt createAttempt(String username, String roomName, String ipAddress, int port) {
        try {
            return new CreationAttempt(service.createRoom(username, roomName, 4, ipAddress, port), null);
        } catch (Throwable throwable) {
            return new CreationAttempt(null, throwable);
        }
    }

    private List<CreationAttempt> runConcurrently(List<Callable<CreationAttempt>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CreationAttempt>> futures = new ArrayList<>();

        try {
            for (Callable<CreationAttempt> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }

            ready.await();
            start.countDown();

            List<CreationAttempt> results = new ArrayList<>();
            for (Future<CreationAttempt> future : futures) {
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

    private FixedCandidateRedisRoomRepository fixedRepository(String... candidates) {
        return new FixedCandidateRedisRoomRepository(
                redisTemplate,
                redisScript("scripts/create_room.lua"),
                redisScript("scripts/join_room.lua"),
                redisScript("scripts/leave_room.lua"),
                List.of(candidates)
        );
    }

    private AmbiguousResultRedisRoomRepository ambiguousRepository(String candidate, AmbiguousCreateMode mode) {
        return new AmbiguousResultRedisRoomRepository(
                redisTemplate,
                redisScript("scripts/create_room.lua"),
                redisScript("scripts/join_room.lua"),
                redisScript("scripts/leave_room.lua"),
                candidate,
                mode
        );
    }

    private Map<String, byte[]> dumpCreationKeys(String roomId) {
        Map<String, byte[]> dumps = new LinkedHashMap<>();
        for (String key : List.of(
                detailsKey(roomId),
                playersKey(roomId),
                "rooms:active_set",
                "player:locations",
                "player:ips")) {
            dumps.put(key, redisTemplate.dump(key));
        }
        return dumps;
    }

    private String detailsKey(String roomId) {
        return "room:" + roomId + ":details";
    }

    private String playersKey(String roomId) {
        return "room:" + roomId + ":players";
    }

    private void createRoomWithHost(
            RedisRoomRepository targetRepository,
            String roomId,
            String username,
            String ipAddress,
            int port
    ) {
        targetRepository.saveNewRoom(roomId, roomId, username, ipAddress, 4);
        targetRepository.addPlayerToRoom(roomId, username);
        targetRepository.addRoomToActiveList(roomId);
        targetRepository.setPlayerLocation(username, roomId);
        targetRepository.savePlayerEndpoint(username, ipAddress, port);
    }

    private record CreationAttempt(RoomDetails roomDetails, Throwable failure) {
        boolean succeeded() {
            return roomDetails != null && failure == null;
        }
    }

    private static class CoordinatedRedisRoomRepository extends RedisRoomRepository {
        private final AtomicInteger generatedCandidateCount = new AtomicInteger();

        CoordinatedRedisRoomRepository(
                StringRedisTemplate redisTemplate,
                RedisScript<Long> createRoomScript,
                RedisScript<Long> joinRoomScript,
                RedisScript<Long> leaveRoomScript
        ) {
            super(redisTemplate, createRoomScript, joinRoomScript, leaveRoomScript);
        }

        @Override
        public String generateNewRoomId() {
            return generatedCandidateCount.getAndIncrement() == 0 ? FIRST_CANDIDATE : SECOND_CANDIDATE;
        }
    }

    private static class FixedCandidateRedisRoomRepository extends RedisRoomRepository {
        private final Queue<String> candidates;

        FixedCandidateRedisRoomRepository(
                StringRedisTemplate redisTemplate,
                RedisScript<Long> createRoomScript,
                RedisScript<Long> joinRoomScript,
                RedisScript<Long> leaveRoomScript,
                List<String> candidates
        ) {
            super(redisTemplate, createRoomScript, joinRoomScript, leaveRoomScript);
            this.candidates = new ConcurrentLinkedQueue<>(candidates);
        }

        @Override
        public String generateNewRoomId() {
            String candidate = candidates.poll();
            if (candidate == null) {
                throw new IllegalStateException("No room ID candidate configured for test");
            }
            return candidate;
        }
    }

    private enum AmbiguousCreateMode {
        COMPLETE,
        COMPLETE_WITH_GUEST,
        ABSENT,
        PARTIAL
    }

    private static class AmbiguousResultRedisRoomRepository extends RedisRoomRepository {
        private final String candidate;
        private final AmbiguousCreateMode mode;

        AmbiguousResultRedisRoomRepository(
                StringRedisTemplate redisTemplate,
                RedisScript<Long> createRoomScript,
                RedisScript<Long> joinRoomScript,
                RedisScript<Long> leaveRoomScript,
                String candidate,
                AmbiguousCreateMode mode
        ) {
            super(redisTemplate, createRoomScript, joinRoomScript, leaveRoomScript);
            this.candidate = candidate;
            this.mode = mode;
        }

        @Override
        public String generateNewRoomId() {
            return candidate;
        }

        @Override
        public long tryCreateRoomAtomically(
                String roomId,
                String roomName,
                String username,
                String hostIp,
                int hostPort,
                int maxPlayers
        ) {
            if (mode == AmbiguousCreateMode.COMPLETE || mode == AmbiguousCreateMode.COMPLETE_WITH_GUEST) {
                long actualResult = super.tryCreateRoomAtomically(
                        roomId, roomName, username, hostIp, hostPort, maxPlayers);
                assertThat(actualResult).isZero();
                if (mode == AmbiguousCreateMode.COMPLETE_WITH_GUEST) {
                    long joinResult = super.tryJoinRoomAtomically(
                            roomId, "response-lost-guest", "127.0.0.32", 6309);
                    assertThat(joinResult).isEqualTo(3L);
                }
            } else if (mode == AmbiguousCreateMode.PARTIAL) {
                saveNewRoom(roomId, roomName, username, hostIp, maxPlayers);
            }
            return -1L;
        }
    }
}
