package com.example.DragDown.Service.AuthService;

import com.example.DragDown.Dto.AuthDto.AuthResponse;
import com.example.DragDown.Dto.AuthDto.ExpiredAccessTokenRequest;
import com.example.DragDown.Exception.TokenRefreshException;
import com.example.DragDown.Repository.MatchRoomRepository;
import com.example.DragDown.Utils.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final MatchRoomRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;


    public AuthResponse refreshToken(ExpiredAccessTokenRequest request) {

        String expiredAccessToken = request.getExpiredAccessToken();

        String username = jwtProvider.getUsernameFromExpiredToken(expiredAccessToken);
        if(username == null){
            throw new TokenRefreshException(expiredAccessToken, "만료된 액세스 토큰이 유효하지 않거나 사용자" +
                    "정보를 추출할 수 없습니다.");
        }

        String savedServerRefreshToken = refreshTokenRepository.findRefreshTokenByUsername(username)
                .orElseThrow(() -> new TokenRefreshException(null,"서버에 해당 사용자의 리프레시 토큰이 없습니다." +
                "다시 로그인해주세요."));

        if(!jwtProvider.validateToken(savedServerRefreshToken)){
        refreshTokenRepository.deleteRefreshTokenByUsername(username);
        log.warn("Stored refresh token for user {} is invalid or expired. Deleting it.", username);
        throw new TokenRefreshException(null, "서버에 저장된 리프레시 토큰이 만료되었거나 유효하지 않습니다." +
                "다시 로그인해주세요.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(username);

        return new AuthResponse(newAccessToken);
    }
    }
