package Parking.exception;

import java.time.LocalDateTime;

import Parking.dto.response.ErrorResponse;
import Parking.exception.exceptions.AuthenticationException;
import Parking.exception.exceptions.BookingException;
import Parking.exception.exceptions.ForbiddenOperationException;
import Parking.exception.exceptions.InvalidTicketStateException;
import Parking.exception.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class APIExceptionHandler {
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<String> handleBookingException(BookingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperationException(
            ForbiddenOperationException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(InvalidTicketStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTicketStateException(
            InvalidTicketStateException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Lỗi kiểm tra dữ liệu");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }



    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolationException(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Lỗi dữ liệu hệ thống");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }
}
