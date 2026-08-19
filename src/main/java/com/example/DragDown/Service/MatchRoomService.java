package com.example.DragDown.Service;

import com.example.DragDown.Dto.MatchDto.RoomDetails;
import com.example.DragDown.Dto.MatchDto.RoomSummary;
import com.example.DragDown.Exception.RoomException;
import com.example.DragDown.Repository.MatchRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRoomService {

    // RoomRepository injection
    private final MatchRoomRepository matchRoomRepository;

    public RoomDetails createRoom(String username, String roomName, int maxPlayers, String hostIpAddress, int hostPort){

        // 1. check if already in other room
        matchRoomRepository.findRoomIdByPlayer(username).ifPresent(existingRoomId ->{
            log.warn("User: '{}' tried to create room but is already in a room '{}'", username, existingRoomId);
            throw new RoomException("이미 방 '" + existingRoomId + "'에 참가 중입니다.");
        });

        // 2.IP address validation check  (maintained in Service layer)
        if(hostIpAddress == null || hostIpAddress.trim().isEmpty() || hostPort <=0){
            throw new RoomException("유효하지 않은 IP 주소 또는 포트 번호입니다.");
        }
        int validatedMaxPlayers = Math.max(2, Math.min(maxPlayers,4)); // Max user limit

        // 3. room creation and related info saved through Repository
        String roomId = matchRoomRepository.generateNewRoomId();
        log.debug("Saving player endpoint for {}", username);
        matchRoomRepository.savePlayerEndpoint(username, hostIpAddress, hostPort);
        log.debug("Saving new room details for {}", roomId);
        matchRoomRepository.saveNewRoom(roomId, roomName, username, hostIpAddress, validatedMaxPlayers);
        log.debug("Adding player to room set for {}", roomId);
        matchRoomRepository.addPlayerToRoom(roomId, username);
        log.debug("Adding room to active list for {}", roomId);
        matchRoomRepository.addRoomToActiveList(roomId);
        log.debug("Setting player location for {}", username);
        matchRoomRepository.setPlayerLocation(username, roomId);
        log.info("Room creation steps completed in service for {}", roomId);

        log.info("Room created via Repository: id={}, name={}, host={}, endpoint={}: {}", roomId, roomName, username,
                hostIpAddress, hostPort);

        // 4. return created room info(view data in Repository, and then create DTO)
        return buildRoomDetailsFromRepository(roomId);

    }

    public List<RoomSummary> listAvailableRooms(){
        Set<String> activeRoomIds = matchRoomRepository.getActiveRoomIds();
        if(activeRoomIds.isEmpty()){
            return Collections.emptyList();
        }

        List<RoomSummary> availableRooms = new ArrayList<>();
        for(String roomId : activeRoomIds){
            Map<String, String> details = matchRoomRepository.getRoomDetailsMap(roomId);

            // if no room info or game started rooms are skipped
            if(details.isEmpty() || !"waiting".equals(details.get("state"))){
                if(details.isEmpty()) log.warn("Active room ID '{}' found but no details in repository.", roomId);
                continue;
            }

            Long playerCount = matchRoomRepository.getRoomPlayerCount(roomId);

            availableRooms.add(new RoomSummary(
                    roomId,
                    details.getOrDefault("name", "Unknown Room"),
                    details.getOrDefault("hostUsername", "Unknown Host"),
                    playerCount.intValue(),
                    Integer.parseInt(details.getOrDefault("maxPlayers", "0")),
                    false // state is 'waiting'
                    ));
        }
        return availableRooms;
    }

    public RoomDetails joinRoom(String username, String joinerIpAddress, String roomId, int joinerPort){

        matchRoomRepository.findRoomIdByPlayer(username).ifPresent(existingRoomId ->{
            throw new RoomException("이미 다른 방 '" + existingRoomId + "'에 참가 중입니다.");
        });

        if(joinerIpAddress == null || joinerIpAddress.trim().isEmpty() || joinerPort <=0){
            throw new RoomException("유효하지 않은 IP 주소 또는 포트 번호입니다.");
        }

        // Lua script execution through Repository
        long result = matchRoomRepository.tryJoinRoomAtomically(roomId, username, joinerIpAddress, joinerPort);

        // result carried out
        return switch ((int) result) {
            case 3 -> {
                log.info("User '{}' joined room '{}' (endpoint : {}:{})", username, roomId, joinerIpAddress, joinerPort);
                yield buildRoomDetailsFromRepository(roomId);
            }
            case 0 -> throw new RoomException("방 참여에 실패했습니다: 게임이 시작되었습니다.");
            case 1 -> throw new RoomException("방 참여에 실패했습니다: 방이 꽉 찼습니다.");
            case 2 -> throw new RoomException("방 참여에 실패했습니다: 이미 해당 방에 참가 중입니다.");
            case 4 -> throw new RoomException("방 '" + roomId + "'을(를) 찾을 수 없습니다.");
            case 5 -> {
                log.error("Repository failed to add player '{}' to room '{}'", username, roomId);
                throw new RoomException("방 참여 중 오류 발생 (플레이어 추가 실패)");
            }
            case -1 -> {
                log.error("Lua script execution failed for joinRoom (user: {}, room: {})", username, roomId);
                throw new RoomException("방 참여 중 오류가 발생했습니다 (스크립트 실패).");
            }
            default -> {
                log.error("Unknown result code {} from repository for joinRoom (user: {}, room: {})", result, username, roomId);
                throw new RoomException(" 방 참여 중 알 수 없는 오류가 발생했습니다.");
            }
        };
    }



    public void leaveRoom(String username){

        Optional<String> roomIdOpt = matchRoomRepository.findRoomIdByPlayer(username);

        if(!roomIdOpt.isPresent()){
            log.info("User '{}' not in any room.", username);
            matchRoomRepository.removePlayerEndpoint(username);
            return;
        }
        String roomId = roomIdOpt.get();

        // getting list of players in the current room to remove them before closing
        Set<String> playersInRoomBeforeLeave = matchRoomRepository.getRoomPlayers(roomId);

        long result = matchRoomRepository.tryLeaveRoomAtomically(username, roomId);

        switch((int) result){
            case 0: // normal exit
                log.info("User '{}' left room '{}' (normal exit)", username, roomId);
                break;
            case 1: // host exit or last member exit(room closed)
                log.warn("User '{}' left room '{}', causing the room to close.", username, roomId);

                List<String> remainingPlayersToClean = new ArrayList<>(playersInRoomBeforeLeave);
                remainingPlayersToClean.remove(username);

                if(!remainingPlayersToClean.isEmpty()){
                    log.info("Cleaning up locations and endpoints for remaining players in closed room {}:{}", roomId, remainingPlayersToClean);
                    matchRoomRepository.removePlayersLocation(remainingPlayersToClean);
                    for(String player : remainingPlayersToClean){
                        matchRoomRepository.removePlayerEndpoint(player);
                    }
                }
                log.info("Room '{}' and its data (details, players, active_set) were deleted by Lua script.", roomId);
                break;
            case 2: // failed (not in player list - Inconsistent)
                String msg2 = String.format("Inconsistency detected: User '%s' was in room '%s' but not in player set.", username, roomId);
                log.error(msg2);
                throw new RoomException(msg2);
            case 3: // failed(no room - Inconsistent
                String msg3 = String.format("Inconsistency detected: User '%s' location points to non-existent room '%s'.", username, roomId);
                log.error(msg3);
                throw new RoomException(msg3);
            case -1: // script execution failed
                String msgNeg1 = String.format("Lua script execution failed for leaveRoom(user: %s, room: %s)", username, roomId);
                log.error(msgNeg1);
                manualCleanupAfterLeaveFailure(username, roomId);
                throw new RoomException("방 나가기 처리 중 오류가 발생했습니다 (스크립트 실패).");
            default:
                String msgDefault = String.format("Unknown result code %d from repository for leaveRoom (user: %s, room: %s)", result, username, roomId);
                log.error(msgDefault);
                throw new RoomException("방 나가기 처리 중 알 수 없는 오류가 발생했습니다.");
        }
    }

    private void manualCleanupAfterLeaveFailure(String username, String roomId){
        log.warn("Attempting manual cleanup for user '{}' after leave script failure in room '{}'", username, roomId);

        try{
            matchRoomRepository.removePlayerLocation(username);
            log.info("Manual cleanup: Removed player location for user '{}'.",username);
            matchRoomRepository.removePlayerEndpoint(username);
            log.info("Manual cleanup: Removed player endpoint for user '{}'.",username);
        }catch(Exception e){
            log.error("Error during manual cleanup for user '{}': {}", username, e.getMessage());
        }
    }

    public void startGame(String username, String roomId){

        Map<String, String> details = matchRoomRepository.getRoomDetailsMap(roomId);
        if(details.isEmpty()){
            throw new RoomException("방 '" + roomId + "'을(를) 찾을 수 없습니다.");
        }

        String hostUsername = details.get("hostUsername");
        String currentState = details.get("state");
        if(!username.equals(hostUsername)){
            throw new RoomException("호스트만 게임을 시작할 수 있습니다.");
        }

        Long playerCount = matchRoomRepository.getRoomPlayerCount(roomId);
        if(playerCount <2){
            throw new RoomException("게임을 시작하기 위한 최소 인원(2명)이 충족되지 않았습니다.");
        }

        matchRoomRepository.updateRoomState(roomId, "started");
        log.info("Game started for room '{}' by host '{}'. State updated via Repository.", roomId, username);
    }


    public Optional<RoomDetails> getRoomDetails(String roomId){

        Map<String,String> details = matchRoomRepository.getRoomDetailsMap(roomId);
        if(details.isEmpty()){
            matchRoomRepository.removeRoomFromActiveList(roomId);
            return Optional.empty();
        }
        return Optional.of(buildRoomDetailsFromRepository(roomId, details));
    }


    public void handleDisconnect(String username){
        log.info("Handling disconnect for user '{}'.", username);

        leaveRoom(username);
        log.info("Finished disconnect hadnling for user '{}'.", username);
    }

    // create RoomDetails DTO based on data searched from Repository
    private RoomDetails buildRoomDetailsFromRepository(String roomId) {
        Map<String,String> details = matchRoomRepository.getRoomDetailsMap(roomId);
        if(details.isEmpty()){
            throw new RoomException("방 정보를 빌드하는 중 오류: 방 '" + roomId + "'없음");
        }
        return buildRoomDetailsFromRepository(roomId,details);
    }
    
    private RoomDetails buildRoomDetailsFromRepository(String roomId, Map<String,String> details) {
        Set<String> playerUsernames = matchRoomRepository.getRoomPlayers(roomId);
        List<String> playerList = new ArrayList<>(playerUsernames);
        Map<String, String> playerEndpointMap = matchRoomRepository.getPlayerEndpoints(playerList);

        String hostUsername = details.getOrDefault("hostUsername", "Unknown Host");
        String hostEndpoint = playerEndpointMap.getOrDefault(hostUsername, ":");
        String[] hostParts = hostEndpoint.split(":",2);
        String hostIp = hostParts[0];
        Integer hostPort = null;
        try {
            if(hostParts.length > 1 && !hostParts[1].isEmpty()){
                hostPort = Integer.parseInt(hostParts[1]);
            }
        }catch (NumberFormatException e){
            log.warn("Could not parse port for host'{}' in room '{}' : {}", hostUsername, roomId,hostParts[1]);
        }

        return new RoomDetails(
                roomId,
                details.getOrDefault("name", "Unknown Room"),
                hostUsername,
                hostIp,
                hostPort,
                Integer.parseInt(details.getOrDefault("maxPlayers", "0")),
                playerList,
                playerEndpointMap,
                "started".equals(details.get("state"))
        );
    }

}
