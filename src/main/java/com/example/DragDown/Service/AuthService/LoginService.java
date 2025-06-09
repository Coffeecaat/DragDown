package com.example.DragDown.Service.AuthService;

import com.example.DragDown.Dto.AuthDto.AuthResponse;
import com.example.DragDown.Dto.AuthDto.LoginRequest;
import com.example.DragDown.Model.Player;
import com.example.DragDown.Repository.MatchRoomRepository;
import com.example.DragDown.Repository.PlayerRepository;
import com.example.DragDown.Utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MatchRoomRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationMs;

    public AuthResponse login(LoginRequest request){
        Player player = playerRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if(!passwordEncoder.matches(request.getPassword(), player.getPassword())){
            throw new BadCredentialsException("Invalid username or password");
        }

        String username = player.getUsername();
        String accessToken = jwtProvider.generateAccessToken(username);
        String serverInternalRefreshToken = jwtProvider.generateRefreshToken(username);


        refreshTokenRepository.deleteRefreshTokenByUsername(username);
        refreshTokenRepository.saveRefreshToken(username, serverInternalRefreshToken, refreshTokenExpirationMs);

        log.info("User '{}' logged in successfully. Tokens generated.", username);
        return new AuthResponse(accessToken);
    }



}
