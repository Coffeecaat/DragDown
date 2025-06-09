package com.example.DragDown.Dto.AuthDto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExpiredAccessTokenRequest {

    @NotBlank(message = "Expired access token is required")
    private String expiredAccessToken;

}
