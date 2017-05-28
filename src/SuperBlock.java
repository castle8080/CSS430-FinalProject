
public class SuperBlock {

    public final static int DEFAULT_INODE_BLOCKS = 64;
    
    public int totalBlocks;
    public int inodeBlocks;
    public int freeList;
    
    public SuperBlock(int totalBlocks) {

        // Read data from disk.
        byte[] buffer = new byte[Disk.blockSize];
        if (SysLib.rawread(0, buffer) == Kernel.ERROR) {
            throw new FileSystemException("Could not read superblock.");
        }

        this.totalBlocks = SysLib.bytes2int(buffer, 0);
        this.inodeBlocks = SysLib.bytes2int(buffer, 4);
        this.freeList = SysLib.bytes2int(buffer, 8);
        
        // Check for a super block that does not appear to be valid.
        // If it isn't reformat the disk.
        if (this.totalBlocks != totalBlocks || this.inodeBlocks <= 0 || this.freeList <= 0) {
            SysLib.cerr("WARNING: The disk is being auto formatted.\n");
            this.totalBlocks = totalBlocks;
            format();
        }
    }
    
    public void format() {
        format(DEFAULT_INODE_BLOCKS);
    }
    
    public void format(int inodeBlocks) {
        if (inodeBlocks <= 0) {
            throw new FileSystemException("Invalid inodeBlocks: " + inodeBlocks);
        }
        
        this.inodeBlocks = inodeBlocks;
        formatInodes();
        formatFreeList();
    }
    
    public int getFreeBlock() {
        if (this.freeList < 0) {
            // The blocks are exhausted
            return Kernel.ERROR;
        }

        byte[] buffer = new byte[Disk.blockSize];
        if (SysLib.rawread(this.freeList, buffer) == Kernel.ERROR) {
            return Kernel.ERROR;
        }
        
        int result = this.freeList;
        this.freeList = SysLib.bytes2int(buffer, 0);
        
        // TODO: should I write out the superblock now?
        
        return result;
    }
    
    public boolean returnBlock(int block) {
        if (block < getInitialFreeBlock() || block >= this.totalBlocks) {
            return false;
        }
        

        byte[] buffer = new byte[Disk.blockSize];
        SysLib.int2bytes(this.freeList, buffer, 0);
        if (SysLib.rawwrite(block, buffer) == Kernel.ERROR) {
            return false;
        }
        
        this.freeList = block;
        return true;
    }
    
    public void sync() {
        byte[] buffer = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, buffer, 0);
        SysLib.int2bytes(inodeBlocks, buffer, 4);
        SysLib.int2bytes(freeList, buffer, 8);
        if (SysLib.rawwrite(0, buffer) == Kernel.ERROR) {
            throw new FileSystemException("Could not write super block.");
        }
    }
    
    private int getRequiredBlocksForInodes() {
        int inodesPerBlock = Disk.blockSize / Inode.iNodeSize;
        int neededInodeBlocks = inodeBlocks / inodesPerBlock;
        if (inodeBlocks % inodesPerBlock > 0) {
            neededInodeBlocks++;
        }
        return neededInodeBlocks;
    }
    
    private int getInitialFreeBlock() {
        return getRequiredBlocksForInodes() + 1;
    }
    
    private void formatInodes() {
        // Write out the inodes.
        // Note: this is very inefficient, it would be nice to write multiple inodes
        //       at the same time to the same block or once all of the code is written
        //       we could take a shot at using cache reads and writes.
        for (short iNumber = 0; iNumber < inodeBlocks; iNumber++) {
            // Create an empty and unused inode.
            Inode inode = new Inode();
            inode.length = 0;
            inode.count = 0;
            inode.flag = 0;
            inode.toDisk(iNumber);
        }
    }
    
    private void formatFreeList() {
        this.freeList = getInitialFreeBlock();
        byte[] buffer = new byte[Disk.blockSize];
        for (int i = this.freeList; i < totalBlocks; i++) {
            // -1 is the end of the free list.
            int next = (i + 1 == totalBlocks) ? -1 : i + 1;
            SysLib.int2bytes(next, buffer, 0);
            if (SysLib.rawwrite(i, buffer) == Kernel.ERROR) {
                throw new FileSystemException("Failed to write during format.");
            }
        }
    }
}
