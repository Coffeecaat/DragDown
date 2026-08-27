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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("redis-integration")
@Testcontainers
class RedisRoomLifecycleIntegrationTest {

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
    void rejectsJoiningMissingRoomWithoutSavingPlayerState() {
        assertThatThrownBy(() -> service.joinRoom("guest", "127.0.0.1", "missing-room", 2001))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("찾을 수 없습니다");

        assertPlayerStateRemoved("missing-room", "guest");
    }

    @Test
    void rejectsJoiningStartedRoomWithoutSavingPlayerState() {
        String roomId = "room-started";
        createRoomWithHost(roomId, "host", 4);
        repository.updateRoomState(roomId, "started");

        assertThatThrownBy(() -> service.joinRoom("guest", "127.0.0.1", roomId, 2002))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("게임이 시작되었습니다");

        assertPlayerStateRemoved(roomId, "guest");
    }

    @Test
    void rejectsJoiningFullRoomWithoutSavingPlayerState() {
        String roomId = "room-full";
        createRoomWithHost(roomId, "host", 2);
        service.joinRoom("first-guest", "127.0.0.1", roomId, 2003);

        assertThatThrownBy(() -> service.joinRoom("second-guest", "127.0.0.1", roomId, 2004))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("방이 꽉 찼습니다");

        assertPlayerStateRemoved(roomId, "second-guest");
        assertThat(repository.getRoomPlayerCount(roomId)).isEqualTo(2L);
    }

    @Test
    void reportsDuplicateJoinToSameRoomPrecisely() {
        String roomId = "room-duplicate";
        createRoomWithHost(roomId, "host", 4);
        service.joinRoom("guest", "127.0.0.1", roomId, 2005);

        assertThatThrownBy(() -> service.joinRoom("guest", "127.0.0.1", roomId, 2006))
                .isInstanceOf(RoomException.class)
                .hasMessage("이미 해당 방에 참가 중입니다.");

        assertThat(repository.getRoomPlayerCount(roomId)).isEqualTo(2L);
        assertThat(repository.findRoomIdByPlayer("guest")).contains(roomId);
        assertThat(endpointOf("guest")).isEqualTo("127.0.0.1:2005");
    }

    @Test
    void leavingWithoutRoomIsIdempotentAndRemovesStaleEndpoint() {
        repository.savePlayerEndpoint("guest", "127.0.0.1", 2007);

        service.leaveRoom("guest");

        assertThat(repository.findRoomIdByPlayer("guest")).isEmpty();
        assertThat(endpointOf("guest")).isEqualTo("Endpoint 정보 없음");
    }

    @Test
    void ordinaryLeaveKeepsRoomAndRemovesOnlyLeavingPlayerState() {
        String roomId = "room-ordinary-leave";
        createRoomWithHost(roomId, "host", 4);
        service.joinRoom("guest", "127.0.0.1", roomId, 2008);

        service.leaveRoom("guest");

        assertThat(repository.getRoomDetailsMap(roomId)).isNotEmpty();
        assertThat(repository.getActiveRoomIds()).contains(roomId);
        assertThat(repository.getRoomPlayers(roomId)).containsExactly("host");
        assertThat(repository.findRoomIdByPlayer("host")).contains(roomId);
        assertPlayerStateRemoved(roomId, "guest");
    }

    @Test
    void hostLeaveClosesRoomAndRemovesAllPlayerState() {
        String roomId = "room-host-leave";
        createRoomWithHost(roomId, "host", 4);
        service.joinRoom("first-guest", "127.0.0.1", roomId, 2009);
        service.joinRoom("second-guest", "127.0.0.1", roomId, 2010);

        service.leaveRoom("host");

        assertThat(repository.getRoomDetailsMap(roomId)).isEmpty();
        assertThat(repository.getRoomPlayers(roomId)).isEmpty();
        assertThat(repository.getActiveRoomIds()).doesNotContain(roomId);
        assertThat(repository.findRoomIdByPlayer("host")).isEmpty();
        assertThat(repository.findRoomIdByPlayer("first-guest")).isEmpty();
        assertThat(repository.findRoomIdByPlayer("second-guest")).isEmpty();
        assertThat(endpointOf("host")).isEqualTo("Endpoint 정보 없음");
        assertThat(endpointOf("first-guest")).isEqualTo("Endpoint 정보 없음");
        assertThat(endpointOf("second-guest")).isEqualTo("Endpoint 정보 없음");
    }

    @Test
    void staleLeaveRequestDoesNotDeleteStateAfterPlayerJoinsAnotherRoom() {
        String oldRoomId = "room-old-still-exists";
        String newRoomId = "room-new-after-leave";
        String username = "moving-player";
        createRoomWithHost(oldRoomId, "old-host", 4);
        service.joinRoom(username, "127.0.0.1", oldRoomId, 2101);

        String staleRoomId = repository.findRoomIdByPlayer(username).orElseThrow();
        service.leaveRoom(username);

        createRoomWithHost(newRoomId, "new-host", 4);
        service.joinRoom(username, "127.0.0.2", newRoomId, 2201);

        long result = repository.tryLeaveRoomAtomically(username, staleRoomId);

        assertThat(result).isEqualTo(4L);
        assertPlayerStillInNewRoom(username, newRoomId, "127.0.0.2:2201");
        assertThat(repository.getRoomDetailsMap(oldRoomId)).isNotEmpty();
    }

    @Test
    void staleLeaveRequestForDeletedRoomDoesNotDeleteNewRoomState() {
        String oldRoomId = "room-old-deleted";
        String newRoomId = "room-new-after-close";
        String username = "former-host";
        createRoomWithHost(oldRoomId, username, 4);

        String staleRoomId = repository.findRoomIdByPlayer(username).orElseThrow();
        service.leaveRoom(username);
        assertThat(repository.getRoomDetailsMap(oldRoomId)).isEmpty();

        createRoomWithHost(newRoomId, "new-host", 4);
        service.joinRoom(username, "127.0.0.3", newRoomId, 2301);

        long result = repository.tryLeaveRoomAtomically(username, staleRoomId);

        assertThat(result).isEqualTo(4L);
        assertPlayerStillInNewRoom(username, newRoomId, "127.0.0.3:2301");
    }

    private void createRoomWithHost(String roomId, String hostUsername, int maxPlayers) {
        repository.saveNewRoom(roomId, roomId, hostUsername, "127.0.0.1", maxPlayers);
        repository.addPlayerToRoom(roomId, hostUsername);
        repository.setPlayerLocation(hostUsername, roomId);
        repository.savePlayerEndpoint(hostUsername, "127.0.0.1", 7777);
        repository.addRoomToActiveList(roomId);
    }

    private void assertPlayerStateRemoved(String roomId, String username) {
        assertThat(repository.getRoomPlayers(roomId)).doesNotContain(username);
        assertThat(repository.findRoomIdByPlayer(username)).isEmpty();
        assertThat(endpointOf(username)).isEqualTo("Endpoint 정보 없음");
    }

    private void assertPlayerStillInNewRoom(String username, String roomId, String endpoint) {
        assertThat(repository.findRoomIdByPlayer(username)).contains(roomId);
        assertThat(endpointOf(username)).isEqualTo(endpoint);
        assertThat(repository.getRoomPlayers(roomId)).contains(username);
        assertThat(repository.getRoomDetailsMap(roomId)).isNotEmpty();
        assertThat(repository.getActiveRoomIds()).contains(roomId);
    }

    private String endpointOf(String username) {
        return repository.getPlayerEndpoints(List.of(username)).get(username);
    }

    private RedisScript<Long> redisScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
