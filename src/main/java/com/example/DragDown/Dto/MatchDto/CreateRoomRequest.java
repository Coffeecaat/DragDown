package com.example.DragDown.Dto.MatchDto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "방 이름은 필수입니다.")
    @Size(min =2, max = 20, message = "방 이름은 2자 이상 20자 이하만 됩니다.")
    private String roomName;

    @Min(value = 2, message = "최소 인원은 2명입니다.")
    @Max(value =4, message = "최대 인원은 4명입니다.")
    private int maxPlayers = 4; // default 4 users

    @NotBlank(message = "IP 주소는 필수입니다.")
    private String ipAddress;

    @NotNull(message = "포트 번호는 필수입니다.")
    @Min(value = 1024, message = "포트 번호는 1024 이상이어야 합니다.")
    @Max(value = 65535, message = "포트 번호는 655356 이하만 됩니다.")
    private Integer port;
}
