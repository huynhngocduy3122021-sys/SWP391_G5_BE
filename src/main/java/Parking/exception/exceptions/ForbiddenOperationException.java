package Parking.exception.exceptions;

public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
