package com.example.DragDown.Utils;


import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtProvider {

    private final SecretKey key;
    private final long jwtExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.jwtExpirationMs =expirationMs;
                }

                public String generateToken(String username){
        return Jwts.builder()
                .subject(username) // 사용자 식별값(아이디)
                .issuedAt(new Date()) // 토큰 발급 시간
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // 만료 시간
                .signWith(key) // 서명
                .compact();
                }


                public boolean validateToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e){
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
                }


                public String getUsernameFromToken(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
                }
}
