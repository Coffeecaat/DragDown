package com.example.DragDown.Controller;

import com.example.DragDown.Dto.*;
import com.example.DragDown.Dto.AuthDto.AuthResponse;
import com.example.DragDown.Dto.AuthDto.ExpiredAccessTokenRequest;
import com.example.DragDown.Dto.AuthDto.LoginRequest;
import com.example.DragDown.Dto.AuthDto.SignupRequest;
import com.example.DragDown.Exception.TokenRefreshException;
import com.example.DragDown.Repository.MatchRoomRepository;
import com.example.DragDown.Service.AuthService.SignUpService;
import com.example.DragDown.Service.AuthService.TokenRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final com.example.DragDown.Service.AuthService.LoginService LoginService;
    private final SignUpService signUpService;
    private final TokenRefreshService tokenRefreshService;
    private final MatchRoomRepository refreshTokenRepository;


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest){
        AuthResponse response = LoginService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody SignupRequest request){
        signUpService.signUp(request);
        return ResponseEntity.ok("User registered successfully");
    }
}
