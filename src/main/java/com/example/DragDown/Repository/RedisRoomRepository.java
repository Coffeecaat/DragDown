package com.example.DragDown.Repository;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class RedisRoomRepository implements MatchRoomRepository{

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> joinRoomScriptBean;
    private final RedisScript<Long> leaveRoomScriptBean;

    public RedisRoomRepository(StringRedisTemplate stringRedisTemplate,
                               @Qualifier("joinRoomScript") RedisScript<Long> joinRoomScript,
                               @Qualifier("leaveRoomScript") RedisScript<Long> leaveRoomScript){
        this.stringRedisTemplate = stringRedisTemplate;
        this.joinRoomScriptBean = joinRoomScript;
        this.leaveRoomScriptBean = leaveRoomScript;
    }

    // -- Redis Key Constants --
    private static final String ROOMS_ACTIVE_SET_KEY = "rooms:active_set";
    private static final String ROOM_DETAILS_HASH_KEY_PREFIX = "room:";
    private static final String ROOM_PLAYERS_SET_KEY_PREFIX = "room:";
    private static final String PLAYER_LOCATIONS_HASH_KEY = "player:locations";
    private static final String PLAYER_IPS_HASH_KEY = "player:ips";
    private static final String USER_REFRESH_TOKEN_KEY_PREFIX = "user:refresh:";

    private static final int MAX_ID_GENERATION_ATTEMPTS = 10;


    @Override
    public void saveRefreshToken(String username, String refreshToken, long ttlMillis){
        String key = USER_REFRESH_TOKEN_KEY_PREFIX + username;
        stringRedisTemplate.opsForValue().set(key,refreshToken, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Saved refresh token for user: {} with TTL: {}ms", username, ttlMillis);
    }

    @Override
    public Optional<String> findRefreshTokenByUsername(String username){
        String key = USER_REFRESH_TOKEN_KEY_PREFIX + username;
        String refreshToken = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(refreshToken);
    }

    @Override
    public void deleteRefreshTokenByUsername(String username){
        String key = USER_REFRESH_TOKEN_KEY_PREFIX + username;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.debug("Deleted refresh token for user: {} with TTL: {}", username, deleted);
    }

    // --Player Location & IP ---
    @Override
    public Optional<String> findRoomIdByPlayer(String username){

        String roomId = stringRedisTemplate.<String,String>opsForHash().get(PLAYER_LOCATIONS_HASH_KEY, username);
        return Optional.ofNullable(roomId);
    }


    @Override
    public void savePlayerEndpoint(String username, String ipAddress, int port){
        String endpoint = ipAddress + ":" + port;

        //save ipAddress as serialized JSON
        stringRedisTemplate.opsForHash().put(PLAYER_IPS_HASH_KEY, username, endpoint);
    }

    @Override
    public void removePlayerEndpoint(String username){
        stringRedisTemplate.opsForHash().delete(PLAYER_IPS_HASH_KEY, username);
    }

    @Override
    public Map<String, String> getPlayerEndpoints(List<String> usernames){

        if(usernames == null || usernames.isEmpty()){
            return Collections.emptyMap();
        }

        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        List<String> endpoints = hashOps.multiGet(PLAYER_IPS_HASH_KEY, usernames);

        Map<String, String> resultMap = new HashMap<>();
        for (int i = 0; i < usernames.size(); i++) {
            String endpointValue = "Endpoint 정보 없음";
            if (endpoints != null && i < endpoints.size() && endpoints.get(i) != null) {
                endpointValue = endpoints.get(i);
            }
            resultMap.put(usernames.get(i), endpointValue);
        }
        return resultMap;

    }

    @Override
    public void setPlayerLocation(String username, String roomId){
        stringRedisTemplate.opsForHash().put(PLAYER_LOCATIONS_HASH_KEY, username, roomId);
    }

    @Override
    public void removePlayerLocation(String username){
        stringRedisTemplate.opsForHash().delete(PLAYER_LOCATIONS_HASH_KEY, username);
    }

    @Override
    public void removePlayersLocation(List<String> usernames){
        if(usernames != null && !usernames.isEmpty()){
            Object[] usernameArray = usernames.toArray();
            stringRedisTemplate.opsForHash().delete(PLAYER_LOCATIONS_HASH_KEY, usernameArray);
        }
    }

    @Override
    public String generateNewRoomId(){

        int attempts =0;
        while( attempts < MAX_ID_GENERATION_ATTEMPTS){

            // 1. random ID generation
            String candidateId = "room-" +UUID.randomUUID().toString().substring(0,6);
            String detailsKey = getRoomDetailsKey(candidateId); //  ID's details key generated with candidate

            // 2. check if candidate key exists in Redis (using hasKey)
            // if key exists -> hasKey = true, if not -> false
            Boolean exists = stringRedisTemplate.hasKey(detailsKey);

            // 3. if key doesn't exist, unique ID found
            if(Boolean.FALSE.equals(exists)){
                log.info("Generated unique room ID '{}' after {} attempt(s).", candidateId, attempts +1);
                return candidateId;
            }

            // 4. if Key already exists, one attempt increment and then retry
            attempts++;
            log.warn("Room ID collision detected for '{}'. Retrying generation (attempt {}/{}...",
                    candidateId, attempts, MAX_ID_GENERATION_ATTEMPTS);
        }

        // 5. if exceeds maximum trial error occurs
        log.error("Failed to generate a unique room ID after {} attempts.", MAX_ID_GENERATION_ATTEMPTS);
        throw new RuntimeException("Could not generate a unique room ID after " + MAX_ID_GENERATION_ATTEMPTS +
                " attempts.");
    }

    private String getRoomDetailsKey(String roomId){
        return ROOM_DETAILS_HASH_KEY_PREFIX + roomId + ":details";
    }
    private String getRoomPlayersKey(String roomId){
        return ROOM_PLAYERS_SET_KEY_PREFIX  + roomId + ":players";
    }


    @Override
    public void saveNewRoom(String roomId, String roomName, String hostUsername, String hostIp, int maxPlayers){

        String roomDetailsKey = getRoomDetailsKey(roomId);
        Map<String, String> roomDetails = new HashMap<>();
        roomDetails.put("name", roomName);
        roomDetails.put("hostUsername", hostUsername);
        roomDetails.put("hostIp", hostIp);
        roomDetails.put("maxPlayers", String.valueOf(maxPlayers));
        roomDetails.put("state", "waiting");
        roomDetails.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        stringRedisTemplate.opsForHash().putAll(roomDetailsKey, roomDetails);
    }

    @Override
    public void addRoomToActiveList(String roomId){
        stringRedisTemplate.opsForSet().add(ROOMS_ACTIVE_SET_KEY, roomId);
    }

    @Override
    public Set<String> getActiveRoomIds(){

        // member() return Set<Object>
        Set<String> roomIds = stringRedisTemplate.opsForSet().members(ROOMS_ACTIVE_SET_KEY);
        return roomIds != null ? roomIds : Collections.emptySet();
    }

    @Override
    public Map<String, String> getRoomDetailsMap(String roomId){
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        return hashOps.entries(getRoomDetailsKey(roomId));
    }

    @Override
    public Set<String> getRoomPlayers(String roomId){

        // member() returns Set<Object>
        Set<String> players = stringRedisTemplate.opsForSet().members(getRoomPlayersKey(roomId));
        return players != null ? players : Collections.emptySet();
    }

    @Override
    public Long getRoomPlayerCount(String roomId){
        // size() returns Long
        Long count = stringRedisTemplate.opsForSet().size(getRoomPlayersKey(roomId));
        return count != null ? count : 0L;
    }

    @Override
    public String getRoomState(String roomId){
        return stringRedisTemplate.<String,String>opsForHash().get(getRoomDetailsKey(roomId), "state");
    }

    @Override
    public void addPlayerToRoom(String roomId, String username){
        stringRedisTemplate.opsForSet().add(getRoomPlayersKey(roomId), username);
    }

    @Override
    public void removeRoomFromActiveList(String roomId){
        stringRedisTemplate.opsForSet().remove(ROOMS_ACTIVE_SET_KEY, roomId);
    }

    @Override
    public void deleteRoomData(String roomId){
        stringRedisTemplate.delete(Arrays.asList(getRoomDetailsKey(roomId), getRoomPlayersKey(roomId)));
    }

    @Override
    public void updateRoomState(String roomId, String newState){
        stringRedisTemplate.opsForHash().put(getRoomDetailsKey(roomId), "state", newState);
    }

    // Atomic Operation (Lua)

    @Override
    public long tryJoinRoomAtomically(String roomId, String username, String joinerIp, int joinerPort){
        List<String> keys = Arrays.asList(
                getRoomPlayersKey(roomId),
                getRoomDetailsKey(roomId),
                PLAYER_LOCATIONS_HASH_KEY,
                PLAYER_IPS_HASH_KEY
        );
        Long result = stringRedisTemplate.execute(joinRoomScriptBean, keys, username, roomId, joinerIp, String.valueOf(joinerPort));
        return result != null ? result : -1L;
    }

    @Override
    public long tryLeaveRoomAtomically(String username, String roomId) {
        List<String> keys = Arrays.asList(
                PLAYER_LOCATIONS_HASH_KEY,
                PLAYER_IPS_HASH_KEY,
                getRoomPlayersKey(roomId),
                getRoomDetailsKey(roomId),
                ROOMS_ACTIVE_SET_KEY
        );
        Long result = stringRedisTemplate.execute(leaveRoomScriptBean, keys, username, roomId);
        return result != null ? result : -1L;
    }
}
