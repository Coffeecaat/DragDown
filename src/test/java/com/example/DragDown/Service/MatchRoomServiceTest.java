package com.example.DragDown.Service;

import com.example.DragDown.Exception.RoomException;
import com.example.DragDown.Repository.MatchRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchRoomServiceTest {

    @Mock
    private MatchRoomRepository repository;

    @InjectMocks
    private MatchRoomService service;

    @Test
    void unknownLeaveScriptResultDoesNotDeletePotentiallyNewPlayerState() {
        String username = "player";
        String roomId = "room-old";
        when(repository.findRoomIdByPlayer(username)).thenReturn(Optional.of(roomId));
        when(repository.tryLeaveRoomAtomically(username, roomId)).thenReturn(-1L);

        assertThatThrownBy(() -> service.leaveRoom(username))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("스크립트 실패");

        verify(repository, never()).removePlayerLocation(username);
        verify(repository, never()).removePlayerEndpoint(username);
    }

    @Test
    void staleLeaveResultReturnsIdempotentlyWithoutDeletingCurrentState() {
        String username = "player";
        String staleRoomId = "room-old";
        when(repository.findRoomIdByPlayer(username)).thenReturn(Optional.of(staleRoomId));
        when(repository.tryLeaveRoomAtomically(username, staleRoomId)).thenReturn(4L);

        assertThatCode(() -> service.leaveRoom(username)).doesNotThrowAnyException();

        verify(repository, never()).removePlayerLocation(username);
        verify(repository, never()).removePlayerEndpoint(username);
    }
}
