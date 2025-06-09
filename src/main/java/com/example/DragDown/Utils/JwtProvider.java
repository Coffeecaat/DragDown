package com.example.DragDown.Utils;


import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration}") long refreshTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(String username) {
        return Jwts.builder()
                .subject(username) // 사용자 식별값(아이디)
                .issuedAt(new Date()) // 토큰 발급 시간
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs)) // 만료 시간
                .signWith(key) // 서명
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(key)
                .compact();
    }


    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", token);
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported Jwt token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token (malformed): {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }


    public String getUsernameFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            log.warn("Attempting to get username from expired token: {}", token);
            return e.getClaims().getSubject();
        } catch (Exception e) {
            log.error("Error parsing username from token: {}", e.getMessage());
            return null;
        }
    }

    public String getUsernameFromExpiredToken(String token) {
        try{
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            log.warn("Extracting username from an EXPIRED token: {}", token);
            return e.getClaims().getSubject();
        }catch (Exception e) {
            log.error("Error parsing username from token (possibly invalid or malformed, not just expired): {}"
                    , e.getMessage());
            return null;
        }
    }

    public boolean isTokenExpired(String token) {
        try{
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return false;
        }
        catch(Exception e){
            return true;
        }
    }

}
