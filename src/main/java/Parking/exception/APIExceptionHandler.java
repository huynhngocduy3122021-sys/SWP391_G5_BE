package Parking.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import Parking.exception.exceptions.AuthenticationException;
import Parking.exception.exceptions.BookingException;


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

    @ExceptionHandler(Parking.exception.exceptions.ResourceNotFoundException.class)
    public ResponseEntity<Parking.dto.response.ErrorResponse> handleResourceNotFoundException(
            Parking.exception.exceptions.ResourceNotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        Parking.dto.response.ErrorResponse response = Parking.dto.response.ErrorResponse.builder()
                .code(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Parking.exception.exceptions.ForbiddenOperationException.class)
    public ResponseEntity<Parking.dto.response.ErrorResponse> handleForbiddenOperationException(
            Parking.exception.exceptions.ForbiddenOperationException ex, jakarta.servlet.http.HttpServletRequest request) {
        Parking.dto.response.ErrorResponse response = Parking.dto.response.ErrorResponse.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .message(ex.getMessage())
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Parking.exception.exceptions.InvalidTicketStateException.class)
    public ResponseEntity<Parking.dto.response.ErrorResponse> handleInvalidTicketStateException(
            Parking.exception.exceptions.InvalidTicketStateException ex, jakarta.servlet.http.HttpServletRequest request) {
        Parking.dto.response.ErrorResponse response = Parking.dto.response.ErrorResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    

     @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Lỗi kiểm tra dữ liệu");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }



    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Lỗi dữ liệu hệ thống");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }
}




