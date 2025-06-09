package com.example.DragDown.Dto.MatchDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomSummary {

    private String roomId;
    private String roomName;
    private String hostUsername;
    private int currentPlayerCount;
    private int maxPlayers;
    private boolean GameStarted;
}
