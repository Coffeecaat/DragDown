package com.example.DragDown.Controller;

import com.example.DragDown.Dto.*;
import com.example.DragDown.Dto.MatchDto.CreateRoomRequest;
import com.example.DragDown.Dto.MatchDto.MatchRequest;
import com.example.DragDown.Dto.MatchDto.RoomDetails;
import com.example.DragDown.Dto.MatchDto.RoomSummary;
import com.example.DragDown.Service.MatchRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/MatchRooms")
@RequiredArgsConstructor
public class MatchRoomController {

    private final MatchRoomService matchRoomService;

    //currently authenticated users' name taken (Helper)
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("인증되지 않은 사용자임");
        }
        return authentication.getName();
    }

    @PostMapping
    public ResponseEntity<?> createMatchRoom(@Valid @RequestBody CreateRoomRequest request) {

            String username = getCurrentUsername();
            log.info("방 생성 요청: 사용자='{}', 방 이름 = '{}', 최대 인원={}, Endpoint ='{}':'{}'",
                    username, request.getRoomName(), request.getMaxPlayers(), request.getIpAddress(),
                    request.getPort());
            RoomDetails createdRoom = matchRoomService.createRoom(username, request.getRoomName(),
                    request.getMaxPlayers(), request.getIpAddress(),request.getPort());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdRoom);

    }


    // Available Match rooms list search
    @GetMapping
    public ResponseEntity<?> listRooms() {

            log.info("참여 가능 방 목록 조회 요청");
            List<RoomSummary> rooms = matchRoomService.listAvailableRooms();
            return ResponseEntity.ok(rooms);

    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @Valid @RequestBody MatchRequest request) {

            String username = getCurrentUsername();
            String joinerIpAddress = request.getIpAddress();
            int joinerPort = request.getPort();

            log.info("방 참여 요청: 사용자='{}', 방 ID='{}', Endpoint='{}:{}'",
                    username, roomId, joinerIpAddress, joinerPort);

            RoomDetails joinedRoomDetails = matchRoomService.joinRoom(username, joinerIpAddress, roomId, joinerPort);
            return ResponseEntity.ok(joinedRoomDetails);

    }


    @DeleteMapping("/leave")
    public ResponseEntity<?> leaveRoom() {

            String username = getCurrentUsername();
            log.info("방 나가기 요청: 사용자='{}'", username);
            matchRoomService.leaveRoom(username);
            return ResponseEntity.ok(new MessageResponse("방에서 성공적으로 나갔습니다."));

    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startGame(@PathVariable String roomId) {

            String username = getCurrentUsername();
            log.info("게임 시작 요청: 사용자 ='{}', 방 ID='{}'", username, roomId);
            matchRoomService.startGame(username, roomId);
            return ResponseEntity.ok(new MessageResponse("게임이 성공적으로 시작되었습니다."));

    }


    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomInfo(@PathVariable String roomId) {

            log.debug("방 정보 조회 요청: 방 ID = '{}'", roomId);
            Optional<RoomDetails> roomDetailsOpt = matchRoomService.getRoomDetails(roomId);

            if(roomDetailsOpt.isPresent()) {
                return ResponseEntity.ok(roomDetailsOpt.get());
            }
            else{
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Not Found", "방 '" + roomId + "'을(를) 찾을 수 없습니다."));
            }

    }
}