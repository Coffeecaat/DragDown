package com.example.DragDown.service;

import com.example.DragDown.dto.SignupRequest;
import com.example.DragDown.model.Player;
import com.example.DragDown.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class signUpService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    public void signUp(SignupRequest request) {
        if(playerRepository.findByUsername(request.getUsername()).isPresent()){
            throw new RuntimeException("Username is already in use");
        }

        Player player = new Player();
        player.setUsername(request.getUsername());
        player.setEmail(request.getEmail());
        player.setPassword(passwordEncoder.encode(request.getPassword()));

        playerRepository.save(player);
    }
}
