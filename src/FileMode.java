
/**
 * Constants and utilities for file modes.
 */
public class FileMode {

    public final static String WRITE = "w";
    public final static String READ = "r";
    public final static String APPEND = "a";
    public final static String READ_WRITE = "w+";
    
    public static boolean isValid(String mode) {
        return WRITE.equals(mode) || READ.equals(mode) || APPEND.equals(mode) || READ_WRITE.equals(mode);
    }
    
    public static boolean isReadable(String mode) {
        return READ.equals(mode) || READ_WRITE.equals(mode);
    }
    
    public static boolean isWritable(String mode) {
        return WRITE.equals(mode) || APPEND.equals(mode) || READ_WRITE.equals(mode);
    }
}
