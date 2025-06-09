package com.example.DragDown.Service.AuthService;

import com.example.DragDown.Dto.AuthDto.AuthResponse;
import com.example.DragDown.Dto.AuthDto.LoginRequest;
import com.example.DragDown.Model.Player;
import com.example.DragDown.Repository.MatchRoomRepository;
import com.example.DragDown.Repository.PlayerRepository;
import com.example.DragDown.Utils.JwtProvider;
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
