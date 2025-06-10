package com.example.DragDown.Controller;

import com.example.DragDown.Dto.AuthDto.AuthResponse;
import com.example.DragDown.Dto.AuthDto.LoginRequest;
import com.example.DragDown.Dto.AuthDto.SignupRequest;
import com.example.DragDown.Service.SignUpService;
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

    private final com.example.DragDown.Service.LoginService LoginService;
    private final SignUpService signUpService;

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
