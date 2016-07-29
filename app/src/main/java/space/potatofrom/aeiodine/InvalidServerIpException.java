package space.potatofrom.aeiodine;

/**
 * Created by kevin on 7/28/16.
 */
public class InvalidServerIpException extends RuntimeException {
    public InvalidServerIpException(String message, Throwable e) {
        super(message, e);
    }

    public InvalidServerIpException(String message) {
        super(message);
    }
}
