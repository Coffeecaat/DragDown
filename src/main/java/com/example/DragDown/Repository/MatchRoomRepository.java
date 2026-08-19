package com.example.DragDown.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MatchRoomRepository {

    Optional<String> findRoomIdByPlayer(String username);
    void savePlayerEndpoint(String username, String ipAddress, int port);
    void removePlayerEndpoint(String username);
    Map<String, String> getPlayerEndpoints(List<String> usernames);

    void saveRefreshToken(String username, String refreshToken, long ttlMillis);
    void deleteRefreshTokenByUsername(String username);
    Optional<String> findRefreshTokenByUsername(String username);

    String generateNewRoomId();
    void saveNewRoom(String roomId, String roomName, String hostUsername, String hostIp, int maxPlayers);
    void addRoomToActiveList(String roomId);
    Set<String> getActiveRoomIds();

    Map<String, String> getRoomDetailsMap(String roomId);
    Set<String> getRoomPlayers(String roomId);
    Long getRoomPlayerCount(String roomId);
    String getRoomState(String roomId);

    void addPlayerToRoom(String roomId, String username);
    void setPlayerLocation(String username, String roomId);

    // Lua script execution method(result return code)
    long tryJoinRoomAtomically(String roomId, String username, String joinerIp, int joinerPort);
    long tryLeaveRoomAtomically(String username, String roomId);

    void removeRoomFromActiveList(String roomId);
    void deleteRoomData(String roomId);
    void removePlayerLocation(String username); // one user
    void removePlayersLocation(List<String> usernames); // many users

    void updateRoomState(String roomId, String newState);
}
