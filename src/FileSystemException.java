
/**
 * Thrown when a fatal error is encountered with the FileSystem.
 */
public class FileSystemException extends RuntimeException {

    private static final long serialVersionUID = 4195641598380384236L;

    public FileSystemException() {
    }

    public FileSystemException(String msg) {
        super(msg);
    }

    public FileSystemException(Throwable cause) {
        super(cause);
    }

    public FileSystemException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
