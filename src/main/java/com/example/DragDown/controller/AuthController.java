package com.example.DragDown.controller;

import com.example.DragDown.dto.AuthResponse;
import com.example.DragDown.dto.LoginRequest;
import com.example.DragDown.dto.SignupRequest;
import com.example.DragDown.service.loginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final loginService LoginService;
    private final com.example.DragDown.service.signUpService signUpService;

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
