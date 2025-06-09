package com.example.DragDown.Exception;

import com.example.DragDown.Dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex){

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(","));

        log.warn("Validation failed: {}", errorMessage);
        ErrorResponse errorResponse = new ErrorResponse("Validation Failed", errorMessage);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex){
        log.warn("Login failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("Login Failed", ex.getMessage());

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFoundException(UsernameNotFoundException ex){
        log.warn("User lookup failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("Not Found", ex.getMessage());

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<ErrorResponse> handleRoomException(RoomException ex){
        log.warn("RoomException occurred: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse("Room Error", ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        if(ex.getMessage().contains("찾을 수 없습니다")) status = HttpStatus.NOT_FOUND;
        if(ex.getMessage().contains("꽉 찼습니다") || ex.getMessage().contains("시작되었습니다") ||
        ex.getMessage().contains("호스트만")) status = HttpStatus.CONFLICT;

        return new ResponseEntity<>(errorResponse, status);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex){
        log.warn("SecurityException occurred: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("Unauthorized", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(Exception ex){
        log.error("Unhandled exception occurred", ex);
        ErrorResponse errorResponse = new ErrorResponse("Internal Server Error", "서버 내부 오류 발생");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
