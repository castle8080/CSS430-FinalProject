
public class FileSystem {
    
    private final SuperBlock superBlock;
    private Directory root;
    private FileTable fileTable;
    
    /**
     * Create the new FileSystem.
     */
    public FileSystem(int files) {
        superBlock = new SuperBlock(files);
        root = new Directory(this.superBlock.inodeBlocks);
        fileTable = new FileTable(root);
        if (!syncRootFromDisk()) {
            throw new FileSystemException("Could not save initial root directory.");
        }
    }

    public void sync() {
        if (!syncRootToDisk()) {
            // don't throw error and try to save as much to disk as possible.
            SysLib.cerr("ERROR: could not sync root directory.\n");
        }
        superBlock.sync();
    }
    
    public boolean format(int files) {
        // Do not format if the fileTable has open files.
        if (!fileTable.fempty()) {
            return false;
        }
        
        superBlock.format(files);
        root = new Directory(this.superBlock.inodeBlocks);
        fileTable = new FileTable(root);
        
        return true;
    }
    
    public FileTableEntry open(String fileName, String mode) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        if (!FileMode.isValid(mode)) {
            return null;
        }
        
        FileTableEntry ftEntry = fileTable.falloc(fileName, mode);
        
        // In write only mode the rest of the file should be freed
        // if there is not more than 1 instance of the file open already.
        if (FileMode.WRITE.equals(mode) && ftEntry.inode.count > 1) {
            if (!truncate(ftEntry)) {
                close(ftEntry);
                return null;
            }
        }
        
        // In append mode the pointer should advance to the end of the file.
        // Should this actually be done by falloc though?
        if (FileMode.APPEND.equals(mode)) {
            if (seek(ftEntry, 0, Seek.END) == Kernel.ERROR) {
                close(ftEntry);
                return null;
            }
        }

        return ftEntry;
    }
    
    public boolean close(FileTableEntry ftEntry) {
        ftEntry.count--;
        if (ftEntry.count == 0) {
            return fileTable.ffree(ftEntry);
        }
        else {
            return true;
        }
    }
    
    public int fsize(FileTableEntry ftEntry) {
        return ftEntry.inode.length;
    }

    public int read(FileTableEntry ftEntry, byte[] buffer) {
        if (!FileMode.isReadable(ftEntry.mode)) {
            return Kernel.ERROR;
        }
        byte[] blockBuffer = new byte[Disk.blockSize];
        int target = Math.min(ftEntry.seekPtr + buffer.length, ftEntry.inode.length);
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
            int len = Math.min(buffer.length - bufferPos, blockBuffer.length - offset);
            System.arraycopy(blockBuffer, offset, buffer, bufferPos, len);
            
            
            // Update the seek position.
            ftEntry.seekPtr += len;
            bufferPos += len;
        }

        return bufferPos;
    }
    
    public int write(FileTableEntry ftEntry, byte[] buffer) {
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
        for (int i = 0; i < indexBlockData.length; i += 2) {
            int block = SysLib.bytes2short(indexBlockData, i);
            if (block >= 0) {
                if (!superBlock.returnBlock(block)) {
                    SysLib.cerr("ERROR: failed to return block: " + block);
                }
            }
        }
        
        // Save the inode back.
        ftEntry.inode.toDisk(ftEntry.iNumber);

        return true;
    }
    
    public boolean delete(String fileName) {
        
        // Open up the file.
        // If this is the only instance opening the file it's
        // blocks will be cleared out.
        FileTableEntry ftEntry = open(fileName, FileMode.WRITE);
        if (ftEntry == null) {
            return false;
        }
        if (ftEntry.inode.count > 1) {
            // Can't delete if other things have the file open.
            return false;
        }
        
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
        
        if (!root.ifree(iNumber)) {
            // Failed to free the slot.
            return false;
        }
        
        // Write the directory back to disk.
        syncRootToDisk();
        
        return true;
    }
    
    public int seek(FileTableEntry ftEntry, int offset, int whence) {
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
