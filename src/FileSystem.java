/**
 * The FileSystem is the main module for performing operations
 * on the files and the file system structure.
 */
public class FileSystem {

    /** Manages the blocks on the file system. */
    private final SuperBlock superBlock;

    /** The main root directory. */
    private Directory root;

    /** Manages file table entry objects. */
    private FileTable fileTable;
    
    /**
     * Create the new FileSystem.
     *
     * @param totalBlocks The number of blocks available to the file system.
     */
    public FileSystem(int totalBlocks) {
        if (totalBlocks < 0) {
            throw new FileSystemException("Invalid value for totalBlocks (" + totalBlocks + ")");
        }
        superBlock = new SuperBlock(totalBlocks);
        root = new Directory(this.superBlock.inodeBlocks);
        fileTable = new FileTable(root);
        if (!syncRootFromDisk()) {
            throw new FileSystemException("Could not save initial root directory.");
        }
    }

    /**
     * Syncs all data back to disk for the file system.
     */
    public void sync() {
        // Lock the file system while performing sync.
        synchronized (this) {
            if (!syncRootToDisk()) {
                // don't throw error and try to save as much to disk as possible.
                SysLib.cerr("ERROR: could not sync root directory.\n");
            }
            superBlock.sync();
        }
    }

    /**
     * Formats the disk.
     *
     * @param files The number of files to support.
     * @return true on success or false if the disk could not be formatted.
     */
    public boolean format(int files) {
        synchronized (this) {
            // Do not format if the fileTable has open files.
            if (!fileTable.fempty()) {
                return false;
            }

            // Don't allow formatting larger than the number of inodes that can be
            // stored on disk.
            int maxInodes = superBlock.totalBlocks * Disk.blockSize / Inode.iNodeSize;
            if (files > maxInodes) {
                return false;
            }

            superBlock.format(files);
            root = new Directory(this.superBlock.inodeBlocks);
            fileTable = new FileTable(root);

            return true;
        }
    }

    /**
     * Opens a file.
     *
     * The following modes are supported:
     *   r - Read
     *   w - Write
     *   a - Append
     *   w+ - Read and Write
     *
     * @param fileName The filename to open.
     * @param mode The mode of the file.
     * @return A FileTableEntry on success or null on failure.
     */
    public FileTableEntry open(String fileName, String mode) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        if (!FileMode.isValid(mode)) {
            return null;
        }

        FileTableEntry ftEntry = null;
        synchronized (this) {
            ftEntry = fileTable.falloc(fileName, mode);
        }
        if (ftEntry == null) {
            return null;
        }

        // In write only mode the rest of the file should be freed
        // if there is not more than 1 instance of the file open already.
        synchronized (ftEntry) {
            if (FileMode.WRITE.equals(mode) && ftEntry.inode.count <= 1) {
                if (!truncate(ftEntry)) {
                    close(ftEntry);
                    return null;
                }
            }
        }

        return ftEntry;
    }

    /**
     * Closes the file table entry.
     */
    public boolean close(FileTableEntry ftEntry) {
        synchronized (ftEntry) {
            ftEntry.count--;
            if (ftEntry.count == 0) {
                return fileTable.ffree(ftEntry);
            }
            else {
                return true;
            }
        }
    }

    /**
     * Get the size of the file table entry.
     */
    public int fsize(FileTableEntry ftEntry) {
        synchronized (ftEntry) {
            return ftEntry.inode.length;
        }
    }

    /**
     * Reads data into buffer from the file table entry starting
     * at the current seek position.  The seek position will be
     * changed based on how much is read.
     *
     * @param ftEntry The file to read from
     * @param buffer The array to read data into
     * @return How many bytes were read into buffer or -1 on error.
     */
    public int read(FileTableEntry ftEntry, byte[] buffer) {
        synchronized (ftEntry) {
            if (!FileMode.isReadable(ftEntry.mode)) {
                return Kernel.ERROR;
            }
            byte[] blockBuffer = new byte[Disk.blockSize];

            int ftRemain = ftEntry.inode.length - ftEntry.seekPtr;
            int nToRead = Math.min(ftRemain, buffer.length);
            int target = ftEntry.seekPtr + nToRead;
            int bufferPos = 0;

            while (ftEntry.seekPtr < target) {

                // Find and read in a block from disk.
                int blockNo = ftEntry.inode.findTargetBlock(ftEntry.seekPtr);
                if (blockNo < 0) {
                    return Kernel.ERROR;
                }
                if (SysLib.rawread(blockNo, blockBuffer) < 0) {
                    return Kernel.ERROR;
                }

                // Copy content from block buffer into the buffer.
                int offset = ftEntry.seekPtr % Disk.blockSize;
                int len = Math.min(nToRead - bufferPos, blockBuffer.length - offset);

                System.arraycopy(blockBuffer, offset, buffer, bufferPos, len);

                // Update the seek position.
                ftEntry.seekPtr += len;
                bufferPos += len;
            }

            return bufferPos;
        }
    }

    /**
     * Writes data from buffer into the given file.
     *
     * @param ftEntry The file to write to.
     * @param buffer The data to write.
     * @return The number of bytes read or -1 on error.
     */
    public int write(FileTableEntry ftEntry, byte[] buffer) {
        synchronized (ftEntry) {
            if (!FileMode.isWritable(ftEntry.mode)) {
                return Kernel.ERROR;
            }

            int bufferPos = 0;
            byte[] blockBuffer = new byte[Disk.blockSize];

            while (bufferPos < buffer.length) {
                short blockId = getBlockId(ftEntry);
                if (blockId < 0) {
                    return Kernel.ERROR;
                }

                int offset = ftEntry.seekPtr % Disk.blockSize;
                int len = Math.min(buffer.length - bufferPos,  Disk.blockSize - offset);

                if (SysLib.rawread(blockId, blockBuffer) == Kernel.ERROR) {
                    return Kernel.ERROR;
                }

                System.arraycopy(buffer, bufferPos, blockBuffer, offset, len);
                if (SysLib.rawwrite(blockId,  blockBuffer) == Kernel.ERROR) {
                    return Kernel.ERROR;
                }

                ftEntry.seekPtr += len;
                bufferPos += len;

                if (ftEntry.seekPtr > ftEntry.inode.length) {
                    ftEntry.inode.length = ftEntry.seekPtr;
                }

                // TODO: It seems like this should be able to fail.
                // but thread os inode returns void.
                ftEntry.inode.toDisk(ftEntry.iNumber);
            }

            return bufferPos;
        }
    }

    /**
     * Deletes the specified file.
     *
     * @param fileName The file name to delete.
     * @return true on success or false on error.
     */
    public boolean delete(String fileName) {
        // Open up the file.
        // If this is the only instance opening the file it's
        // blocks will be cleared out.
        FileTableEntry ftEntry = open(fileName, FileMode.WRITE);
        if (ftEntry == null) {
            return false;
        }

        // Lock the whole file system - the inode and iNumber should only be
        // mutated in the ftEntry within a file system lock.
        synchronized (this) {
            // Get the inode to remove.
            short iNumber = -1;
            try {
                if (ftEntry.inode.count > 1) {
                    // This is already open - do not delete.
                    return false;
                }
                iNumber = ftEntry.iNumber;
            }
            finally {
                close(ftEntry);
            }

            // Frees the slot in the directory.
            if (!root.ifree(iNumber)) {
                // Failed to free the slot.
                return false;
            }

            // Write the directory back to disk.
            syncRootToDisk();

            return true;
        }
    }

    /**
     * Change the seek position of the file table entry.
     *
     * The whence parameter is one of:
     *   Seek.SET(0) - Seek from the beginning.
     *   Seek.CUR(1) - Seek from current seek position.
     *   Seek.END(2) - Seek from the end of the file.
     *
     * If the absolute position change would go beyond the end of
     * the file, the seek position will be at the end of the file.
     *
     * If the absolute position change ends up being negative, then
     * the position will be at the beginning of the file.
     *
     * @param ftEntry The file table entry to seek within.
     * @param offset The amount to seek by.
     * @param whence Where to seek from.
     * @return The new postion or Kernel.ERROR on error.
     */
    public int seek(FileTableEntry ftEntry, int offset, int whence) {
        synchronized (ftEntry) {
            int absOffset = -1;

            switch (whence) {
                case Seek.SET:
                    absOffset = offset;
                    break;
                case Seek.CUR:
                    absOffset = ftEntry.seekPtr + offset;
                    break;
                case Seek.END:
                    absOffset = ftEntry.inode.length + offset;
                    break;
                default:
                    return Kernel.ERROR;
            }

            if (absOffset < 0) {
                absOffset = 0;
            }
            else if (absOffset > ftEntry.inode.length) {
                absOffset = ftEntry.inode.length;
            }

            ftEntry.seekPtr = absOffset;
            return absOffset;
        }
    }

    private short getBlockId(FileTableEntry ftEntry) {
        short blockId = (short) ftEntry.inode.findTargetBlock(ftEntry.seekPtr);
        if (blockId >= 0) {
            return blockId;
        }

        // Create a new block
        blockId = (short) superBlock.getFreeBlock();
        if (blockId < 0) {
            return Kernel.ERROR;
        }

        // Add the block to the inode.
        int rc = ftEntry.inode.registerTargetBlock(ftEntry.seekPtr, blockId);
        switch (rc) {
            case Inode.NoError:
                return blockId;
            case Inode.ErrorBlockRegistered:
                // The seek pointer position already had a block allocated.
                return Kernel.ERROR;
            case Inode.ErrorPrecBlockUnused:
                // TODO: what is this condition?
                return Kernel.ERROR;
            case Inode.ErrorIndirectNull:
                // The block should be put in the indirect block but the indirect block hasn't
                // been allocated.
                short indirectBlockId = (short) superBlock.getFreeBlock();
                if (indirectBlockId < 0 || !ftEntry.inode.registerIndexBlock(indirectBlockId)) {
                    // Give back the previously allocated data block(s).
                    superBlock.returnBlock(blockId);
                    if (indirectBlockId >= 0) {
                        superBlock.returnBlock(indirectBlockId);
                    }
                    return Kernel.ERROR;
                }
                if (ftEntry.inode.registerTargetBlock(ftEntry.seekPtr, blockId) != Inode.NoError) {
                    return Kernel.ERROR;
                }
                return blockId;
            default:
                throw new FileSystemException("Unknown response from register target block: " + rc);
        }
    }

    private boolean truncate(FileTableEntry ftEntry) {
        // Free the direct blocks.
        for (int i = 0; i < ftEntry.inode.direct.length; i++) {
            int block = ftEntry.inode.direct[i];
            if (block >= 0) {
                if (!superBlock.returnBlock(block)) {
                    SysLib.cerr("ERROR: failed to return block: " + block);
                }
                ftEntry.inode.direct[i] = -1;
            }
        }

        // Free any extra index blocks from the extended section.
        byte[] indexBlockData = ftEntry.inode.unregisterIndexBlock();
        if (indexBlockData != null) {
            for (int i = 0; i < indexBlockData.length; i += 2) {
                int block = SysLib.bytes2short(indexBlockData, i);
                if (block >= 0) {
                    if (!superBlock.returnBlock(block)) {
                        SysLib.cerr("ERROR: failed to return block: " + block);
                    }
                }
            }
        }

        // Save the inode back.
        ftEntry.inode.toDisk(ftEntry.iNumber);

        return true;
    }

    private boolean syncRootToDisk() {
        FileTableEntry rootFtEntry = open("/", "w");
        if (rootFtEntry == null) {
            return false;
        }
        try {
            byte[] data = root.directory2bytes();
            if (write(rootFtEntry, data) < 0) {
                return false;
            }
        }
        finally {
            close(rootFtEntry);
        }
        return true;
    }
    
    private boolean syncRootFromDisk() {
        FileTableEntry dirEntry = open("/", "r");
        try {
            int dirSize = fsize(dirEntry);
            if (dirSize > 0) {
                byte[] dirData = new byte[dirSize];
                if (read(dirEntry, dirData) < 0) {
                    return false;
                }
                else {
                    root.bytes2directory(dirData);
                    return true;
                }
            }
            else {
                return true;
            }
        }
        finally {
            if (!close(dirEntry)) {
                throw new FileSystemException("Could not close root directory.");
            }
        }
    }
}
