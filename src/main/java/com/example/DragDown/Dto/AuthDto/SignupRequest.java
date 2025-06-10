package com.example.DragDown.Dto.AuthDto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SignupRequest {
    @NotEmpty
    private String username;
    @NotEmpty
    private String email;
    @NotEmpty
    private String password;
}
