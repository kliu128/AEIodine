package space.potatofrom.aeiodine;

/**
 * Created by kevin on 7/28/16.
 */
public class InvalidClientIpException extends RuntimeException {
    public InvalidClientIpException(String message, Throwable e) {
        super(message, e);
    }

    public InvalidClientIpException(String message) {
        super(message);
    }
}
