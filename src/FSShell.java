import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import sun.awt.CharsetString;

/**
 * This class is useful for interactively testing the filesystem.
 */
public class FSShell implements Runnable {
    
    /**
     * Runs the shell.
     * 
     * Enter commands on the shell which correspond to methods in this class.
     * The shell will attempt to auto convert arguments from the command line
     * to the types the methods need.
     */
    @Override
    public void run() {
        try {
            StringBuffer inputBuffer = new StringBuffer();
            while (true) {
                inputBuffer.delete(0, inputBuffer.length());
                SysLib.cout("fs> ");
                if (SysLib.cin(inputBuffer) == Kernel.ERROR) {
                    return;
                }
                execute(inputBuffer.toString().trim().split("\\s+"));
            }
        }
        catch (ExitException e) {
            SysLib.cout("Exiting FSShell.\n");
        }
        finally {
            SysLib.exit();
        }
    }

    public void delete(String fileName) {
        if (SysLib.delete(fileName) != Kernel.OK) {
            SysLib.cerr("Could not delete: " + fileName + "\n");
        }
    }
    
    public void size(String fileName) {
        int fd = SysLib.open(fileName, "r");
        try {
            if (fd < 0) {
                SysLib.cerr("Could not open: " + fileName + "\n");
            }
            else {
                SysLib.cout("Size: " + SysLib.fsize(fd) + " fd:" + fd + "\n");
            }
        }
        finally {
            if (fd >= 0) {
                SysLib.close(fd);
            }
        }
    }
    
    public void read(String fileName) throws Exception {
        int fd = SysLib.open(fileName, "r");
        try {
            if (fd < 0) {
                SysLib.cerr("Could not open: " + fileName + "\n");
            }
            else {
                int size = SysLib.fsize(fd);
                byte[] data = new byte[size];
                if (SysLib.read(fd, data) == Kernel.ERROR) {
                    SysLib.cerr("Failed to read!\n");
                    
                }
                else {
                    SysLib.cout("Content:\n" + new String(data, "UTF-8") + "\n");
                }
            }
        }
        finally {
            if (fd >= 0) {
                SysLib.close(fd);
            }
        }
    }
    
    public void appendLine(String fileName, String content) throws Exception {
        int fd = SysLib.open(fileName, "a");
        try {
            if (fd < 0) {
                SysLib.cerr("Could not open: " + fileName + "\n");
            }
            else {
                if (SysLib.write(fd, (content + "\n").getBytes("UTF-8")) == Kernel.ERROR) {
                    SysLib.cerr("ERROR: could not write content to file.\n");
                }
            }
        }
        finally {
            if (fd >= 0) {
                SysLib.close(fd);
            }
        }
    }
    
    public void write(String fileName, String content) throws Exception {
        int fd = SysLib.open(fileName, "w");
        try {
            if (fd < 0) {
                SysLib.cerr("Could not open: " + fileName + "\n");
            }
            else {
                if (SysLib.write(fd, content.getBytes("UTF-8")) == Kernel.ERROR) {
                    SysLib.cerr("ERROR: could not write content to file.\n");
                }
            }
        }
        finally {
            if (fd >= 0) {
                SysLib.close(fd);
            }
        }
    }
    
    /**
     * Run a format on the file system.
     */
    public void format(int nFiles) {
        if (SysLib.format(nFiles) != Kernel.OK) {
            SysLib.cerr("The format failed.\n");
        }
    }
    
    /**
     * exit the file system shell.
     */
    public void exit() {
        throw new ExitException();
    }

    /**
     * Command to run a sync.
     */
    public void sync() {
        if (SysLib.sync() != Kernel.OK) {
            SysLib.cerr("The sync failed.\n");
        }
    }
    
    /**
     * Dumps the details of the file system objects.
     */
    public void dump() throws Exception {
        // Recurses through the FileSystem outputting the private fields.
        
        FileSystem fs = null;

        // Hack out the file system instance!
        for (Field f : Kernel.class.getDeclaredFields()) {
            if (FileSystem.class.equals(f.getType())) {
                f.setAccessible(true);
                fs = (FileSystem) f.get(null);
                break;
            }
        }
        
        dumpObject("fs", fs, new HashSet<Object>(), 0);
    }
    
    /**
     * Helper for dump.
     */
    private void dumpObject(String name, Object o, Set<Object> visited, int level) throws Exception {
        if (visited.contains(o)) {
            return;
        }
        else {
            visited.add(o);
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level * 4; i++) {
            sb.append(' ');
        }
        
        String sValue = o.toString();
        if (o instanceof char[]) {
            sValue = new String((char[]) o);
        }
        
        SysLib.cout(sb.toString() + name + " [" + sValue + "] " + o.getClass().getSimpleName() + "\n");
        
        if (o instanceof char[]) {
            // skip
        }
        else if (o.getClass().isArray()) {
            int len = Array.getLength(o);
            for (int i = 0; i < len; i++) {
                Object value = Array.get(o, i);
                dumpObject(name + "[" + i + "]", value, visited, level + 1);
            }
        }
        else if (o instanceof Iterable) {
            Iterable<?> iter = (Iterable<?>) o;
            int i = 0;
            for (Object value : iter) {
                dumpObject(name + "[" + i + "]", value, visited, level + 1);
                i++;
            }
        }
        else if (o instanceof String || o.getClass().isPrimitive()) {
            // Already displayed.
        }
        else {
            for (Field field : o.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object value = field.get(o);
                    dumpObject(field.getName(), value, visited, level + 1);
                }
            }
        }
    }
    
    /**
     * Executes the command line.
     */
    private void execute(String[] command) {
        try {
            if (command.length == 0 || command[0].length() == 0) {
                return;
            }
            for (Method m : FSShell.class.getDeclaredMethods()) {
                if (m.getName().equals(command[0])) {
                    execute(m, command);
                    return;
                }
            }
            throw new Exception("Unknown command: " + command[0]);
            
        }
        catch (ExitException e) {
            throw e;
        }
        catch (Throwable e) {
            SysLib.cerr("ERROR: " + e.getMessage() + "\n");
        }
    }
    
    /**
     * Executes the command line against the given method in this class.
     */
    private void execute(Method method, String[] command) throws Throwable {
        
        // Convert the command args to the method args.
        Class<?>[] argTypes = method.getParameterTypes();
        Object[] args = new Object[argTypes.length];
        
        if (command.length - 1 != args.length) {
            throw new IllegalArgumentException("The command " + command[0] + " takes " + args.length + " arguments.");
        }
        
        for (int i = 0; i < args.length; i++) {
            try {
                Class<?> argType = argTypes[i];
                if (int.class.isAssignableFrom(argType)) {
                    args[i] = Integer.parseInt(command[i + 1]);
                }
                else if (long.class.isAssignableFrom(argType)) {
                    args[i] = Long.parseLong(command[i + 1]);
                }
                else if (short.class.isAssignableFrom(argType)) {
                    args[i] = Short.parseShort(command[i + 1]);
                }
                else if (byte.class.isAssignableFrom(argType)) {
                    args[i] = Byte.parseByte(command[i + 1]);
                }
                else if (boolean.class.isAssignableFrom(argType)) {
                    args[i] = Boolean.parseBoolean(command[i + 1]);
                }
                else if (String.class.isAssignableFrom(argType)) {
                    args[i] = command[i + 1];
                }
                else if (Character.class.isAssignableFrom(argType)) {
                    if (command[i + 1].length() != 1) {
                        throw new IllegalArgumentException("The argument is to long");
                    }
                    args[i] = command[i + 1].charAt(0);
                }
                else {
                    throw new IllegalArgumentException("Cannot convert command argument.");
                }
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Could not convert parameter " + i + " to type " +
                    argTypes[i].getSimpleName() + " - " + e.getMessage());
            }
        }
        
        method.setAccessible(true);
        try {
            method.invoke(this, args);
        }
        catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
    
    /**
     * Exception that indicates the shell should exit.
     */
    public static class ExitException extends RuntimeException {
        private static final long serialVersionUID = 8032052731861268905L;
    }
}
