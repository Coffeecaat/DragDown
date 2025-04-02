package com.example.DragDown.service;

import com.example.DragDown.dto.AuthResponse;
import com.example.DragDown.dto.LoginRequest;
import com.example.DragDown.model.Player;
import com.example.DragDown.repository.PlayerRepository;
import com.example.DragDown.utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class loginService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthResponse login(LoginRequest request){
        Player player = playerRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if(!passwordEncoder.matches(request.getPassword(), player.getPassword())){
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtProvider.generateToken(player.getUsername());

        return new AuthResponse(token);
    }


}
