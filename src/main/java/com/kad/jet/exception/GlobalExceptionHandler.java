package com.kad.jet.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory
            .getLogger(GlobalExceptionHandler.class);

    @org.springframework.web.bind.annotation
            .ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> inputValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request){

        logger.warn("validation error: {} ", e.getMessage());

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err-> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        message,
                        "validation error"
                ));

    }

    @org.springframework.web.bind.annotation
            .ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> badJsonBody(
            HttpMessageNotReadableException e,
            HttpServletRequest request){

        logger.warn("bad json passed: {} ", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "invalid request body or data format",
                        "malformed request"
                ));
    }

    @org.springframework.web.bind.annotation
            .ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMisMatchHandler(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request){

        logger.warn("type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "invalid value of parameter: " + ex.getName(),
                        "type mismatch"
                ));
    }

    @org.springframework.web.bind.annotation.
            ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> databaseExceptionHandler(
            DataIntegrityViolationException ex,
            HttpServletRequest request){

        logger.warn("database constraint validation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        request.getRequestURI(),
                        "request violate database constrains",
                        "data conflict"

                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentialExceptionHandler(BadCredentialsException ex,
                                                                       HttpServletRequest request){
        logger.error("bad login credential: {} ", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "bad credential",
                        "bad credential"
                ));
    }

    @org.springframework.web.bind.annotation
            .ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseErrorHandler(
            ResponseStatusException ex,
            HttpServletRequest request){

        logger.info("invalid error type: {}", ex.getMessage());

        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null
                ? ex.getReason() : ex.getMessage();

        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(
                        statusCode,
                        request.getRequestURI(),
                        reason,
                        ex.getMessage()
                ));
    }

    @org.springframework.web.bind.annotation
            .ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(
            Exception ex,
            HttpServletRequest request){

        logger.error("unsupported system error", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        request.getRequestURI(),
                        "An unexpected error occurred",
                        "Internal server error"
                ));
    }

}
