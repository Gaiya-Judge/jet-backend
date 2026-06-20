package com.kad.jet.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;
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
                        "request violates a database constraint. duplicate or related data may already exist",
                        "data conflict"
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentialExceptionHandler(BadCredentialsException ex,
                                                                       HttpServletRequest request){
        logger.error("bad login credential: {} ", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        request.getRequestURI(),
                        "bad credential",
                        "bad credential"
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> NoResourceFoundExceptionHandler(NoResourceFoundException e,
                                                                         HttpServletRequest request){
        logger.error("no resource found: {} ", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        request.getRequestURI(),
                        "no resource found",
                        "not found"
                ));
    }

    @org.springframework.web.bind.annotation
            .ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseErrorHandler(
            ResponseStatusException ex,
            HttpServletRequest request){

        logger.info("response status exception: {}", ex.getReason());

        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null
                ? ex.getReason() : ex.getMessage();

        String error = HttpStatus.resolve(statusCode) != null
                ? HttpStatus.valueOf(statusCode).getReasonPhrase()
                : "request error";

        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(
                        statusCode,
                        request.getRequestURI(),
                        reason,
                        error
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> maxUploadSizeHandler(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        logger.warn("file upload too large: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        request.getRequestURI(),
                        "uploaded file is too large",
                        "payload too large"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgumentHandler(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        logger.warn("illegal argument: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : "invalid request value",
                        "invalid argument"
                ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> optimisticLockHandler(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {

        logger.warn("optimistic locking conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        request.getRequestURI(),
                        "record was updated by another process. please retry",
                        "concurrent update conflict"
                ));
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> databaseLockHandler(
            Exception ex,
            HttpServletRequest request) {

        logger.warn("database lock conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        request.getRequestURI(),
                        "record is currently being processed. please retry shortly",
                        "database lock conflict"
                ));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> multipartExceptionHandler(
            MultipartException ex,
            HttpServletRequest request) {

        logger.warn("multipart request error: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "invalid file upload request",
                        "multipart request error"
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaTypeNotSupportedHandler(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        logger.warn("media type not supported: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        request.getRequestURI(),
                        "content type is not supported for this endpoint",
                        "unsupported media type"
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotSupportedHandler(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        logger.warn("method not supported: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        request.getRequestURI(),
                        "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                        "method not allowed"
                ));
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authenticationExceptionHandler(
            AuthenticationException ex,
            HttpServletRequest request) {

        logger.warn("authentication error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        request.getRequestURI(),
                        "authentication failed",
                        "unauthorized"
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDeniedHandler(
            AccessDeniedException ex,
            HttpServletRequest request) {

        logger.warn("access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        request.getRequestURI(),
                        "you do not have permission to perform this action",
                        "access denied"
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolationHandler(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        logger.warn("constraint violation: {}", ex.getMessage());

        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " +
                        violation.getMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        message,
                        "constraint violation"
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingRequestHeaderHandler(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {

        logger.warn("missing request header: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "required header '" + ex.getHeaderName() + "' is missing",
                        "missing request header"
                ));
    }


    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingRequestParameterHandler(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        logger.warn("missing request parameter: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "required parameter '" + ex.getParameterName() + "' is missing",
                        "missing request parameter"
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
