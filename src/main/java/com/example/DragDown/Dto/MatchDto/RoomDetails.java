package com.example.DragDown.Dto.MatchDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetails {
    private String roomId;
    private String roomName;
    private String hostUsername;
    private String hostIpAddress;
    private Integer hostPort;
    private int maxPlayers;
    private List<String> players; // current list of users
    private Map<String,String> playerIps; // all users' username -> ipAddress mapped
    private boolean GameStarted;


}
