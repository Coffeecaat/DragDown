package com.example.DragDown.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchRequest {

    @NotBlank(message = "IP 주소는 필수입니다.")
    private String ipAddress;

    @NotNull(message = "포트 번호는 필수입니다.")
    @Min(value = 1024, message = "포트 번호는 1024 이상이어야 합니다.")
    @Max(value = 65535, message = "포트 번호는 65535이하만 됩니다.")
    private Integer port;
}
