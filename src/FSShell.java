import java.io.*;
import java.lang.reflect.*;
import java.util.*;

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
    
	/**
	 * Delete a file by file name.
	 */
    public void delete(String fileName) {
        if (SysLib.delete(fileName) != Kernel.OK) {
            SysLib.cerr("Could not delete: " + fileName + "\n");
        }
    }
    
    /**
     * Open a file and print out the file descriptor,
     * which can be used with other commands.
     */
    public void fopen(String fileName, String mode) {
        int fd = SysLib.open(fileName, mode);
        if (fd < 0) {
            SysLib.cerr("Could not open: " + fileName + "\n");
        }
        else {
            SysLib.cout("Opened file: fd=" + fd + "\n");
        }
    }
    
    /**
     * Close the file descriptor.
     */
    public void fclose(int fd) {
        if (SysLib.close(fd) == Kernel.ERROR) {
            SysLib.cerr("Could not close fd " + fd + "\n");
        }
    }
    
    /**
     * Seek a position for the given file descriptor.
     */
    public void fseek(int fd, int offset, int whence) {
        if (SysLib.seek(fd, offset, whence) == Kernel.ERROR) {
            SysLib.cerr("Could not seek!\n");
        }
    }
    
    /**
     * Write a string to the open file.
     */
    public void fwrite(int fd, String content) throws Exception {
        if (SysLib.write(fd, content.getBytes("UTF-8")) == Kernel.ERROR) {
            SysLib.cerr("Could not write!\n");
        }
    }
    
    /**
     * Read size bytes as a string from the file.
     */
    public void fread(int fd, int size) throws Exception { 
        byte[] buffer = new byte[size];
        int nRead = SysLib.read(fd, buffer);
        
        if (nRead == Kernel.ERROR) {
            SysLib.cerr("Failed to read!\n");
        }
        
        SysLib.cout("Read: [" + nRead + "]\n" + new String(buffer, 0, nRead, "UTF-8") + "\n");
    }
    
    /**
     * Get the size of the file by filename.
     */
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
    
    /**
     * Copy a file from the external file system into thread os.
     */
    public void importFile(String externalFile, String internalFile) throws Exception {
    	FileInputStream fis = new FileInputStream(externalFile);
    	try {
    		byte[] buffer = new byte[Disk.blockSize];
    		int fd = SysLib.open(internalFile, "w");
    		if (fd < 0) {
    			throw new IOException("Could not open internal file: " + internalFile);
    		}
    		try {
        		while (true) {
        			int nRead = fis.read(buffer);
        			if (nRead < 0) {
        				return;
        			}
        			int writeResult;
        			if (nRead < buffer.length) {
        				writeResult = SysLib.write(fd, Arrays.copyOf(buffer, nRead));
        			}
        			else {
        				writeResult = SysLib.write(fd, buffer);
        			}
        			if (writeResult < 0) {
        				throw new IOException("Could not write data.");
        			}
        		}
    		}
    		finally {
    			SysLib.close(fd);
    		}
    	}
    	finally {
    		fis.close();
    	}
    }
    
    /**
     * Copy a file from thread os back out to the file system.
     */
    public void exportFile(String internalFile, String externalFile) throws Exception {
		byte[] buffer = new byte[Disk.blockSize];
	    int fd = SysLib.open(internalFile, "r");
		if (fd < 0) {
			throw new IOException("Could not open internal file: " + internalFile);
		}
		try {
			FileOutputStream fos = new FileOutputStream(externalFile);
    		try {
        		while (true) {
        			int nRead = SysLib.read(fd, buffer);
        			if (nRead < 0) {
        				throw new IOException("Could not read data.");
        			}
        			else if (nRead == 0) {
        				return;
        			}
        			fos.write(buffer, 0, nRead);
        		}
    		}
    		finally {
    			fos.close();
    		}
		}
		finally {
			SysLib.close(fd);
		}
    }
    
    /**
     * Read the whole file and print it out as a string.
     */
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
    
    /**
     * Open the file in append mode and write the line to the file.
     */
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
    
    /**
     * Write the given content to the file by filename.
     */
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
     * Dumps out the block free list.
     */
    public void debugFreeList() throws Exception {
        FileSystem fs = getFileSystem();
        SuperBlock sb = getFieldOfType(FileSystem.class, fs, SuperBlock.class);
        
        int node = sb.freeList;
        byte[] buffer = new byte[Disk.blockSize];
        List<Integer> nodes = new ArrayList<Integer>();
        
        while (node >= 0) {
            nodes.add(node);
            if (SysLib.rawread(node, buffer) == Kernel.ERROR) {
                SysLib.cerr("Could not read!");
                return;
            }
            node = SysLib.bytes2int(buffer, 0);
        }
        
        SysLib.cout("Free List:\n" + nodes + "\n");
    }
    
    /**
     * Dumps the details of the file system objects.
     */
    public void dumpfs() throws Exception {
        // Recurses through the FileSystem outputting the private fields.
        dumpObject("fs", getFileSystem(), new HashSet<Object>(), 0);
    }
   
    /**
     * Dumps the details of the current tcb.
     */
    public void dumpfte() throws Exception {
        Scheduler scheduler = getFieldOfType(Kernel.class, null, Scheduler.class);
        TCB tcb = scheduler.getMyTcb();
        FileTableEntry[] entries = getFieldOfType(TCB.class, tcb, FileTableEntry[].class);
        dumpObject("ftes", entries, new HashSet<Object>(), 0);
    }

    /**
     * Helper for dump.
     */
    private void dumpObject(String name, Object o, Set<Object> visited, int level) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level * 4; i++) {
            sb.append(' ');
        }
        
        String sValue = String.valueOf(o);
        if (o instanceof char[]) {
            sValue = new String((char[]) o);
        }
        SysLib.cout(sb.toString() + name + " [" + sValue + "] " + (o == null ? "" : o.getClass().getSimpleName()) + "\n");
        
        if (visited.contains(o)) {
            return;
        }
        else {
            visited.add(o);
        }
        if (o == null) {
            // skip
        }
        else if (o instanceof char[]) {
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
        else if (o instanceof String || o.getClass().isPrimitive() || o instanceof Number) {
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
                if (m.getName().equals(command[0]) && command.length - 1 == m.getParameterTypes().length) {
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
            StringWriter sw = new StringWriter();
            PrintWriter out = new PrintWriter(sw);
            e.printStackTrace(out);
            out.flush();
            SysLib.cerr("ERROR: " + e.getMessage() + "\n" + sw.toString() + "\n");
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
     * Finds the FileSystem instance in ThreadOS. (Hack!)
     */
    private FileSystem getFileSystem() throws Exception {
        return getFieldOfType(Kernel.class, null, FileSystem.class);
    }
    
    /**
     * Finds a field in another object by type.
     */
    @SuppressWarnings("unchecked")
    private <C,T> T getFieldOfType(Class<C> containerClass, C container, Class<T> fieldClass) throws Exception {
        // Hack out the file system instance!
        for (Field f : containerClass.getDeclaredFields()) {
            if (fieldClass.equals(f.getType())) {
                f.setAccessible(true);
                return (T) f.get(container);
            }
        }
        throw new Exception("Couldn't find type: " + fieldClass.getSimpleName());
    }

    /**
     * Exception that indicates the shell should exit.
     */
    public static class ExitException extends RuntimeException {
        private static final long serialVersionUID = 8032052731861268905L;
    }
}
