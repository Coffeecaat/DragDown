package com.example.DragDown.Exception;


public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String token, String message) {
        super(String.format("Failed for token [%s...]: %s",
                (token != null && token.length() > 10 ? token.substring(0,10) : token), message));
    }
}
