

//simplified version of the Unix inode

public class Inode
{
    public static final int iNodeSize = 32;        //fix to 32 bytes
    public static final int directSize = 11;       //# of direct pointers
    private static final int NOT_FOUND = -1;
    public static final int NoError = 0;
    public static final int ErrorBlockRegistered = -1;
    public static final int ErrorPrecBlockUnused = -2;
    public static final int ErrorIndirectNull = -3;


    public int length;                              //# bytes in file (file size)
    public short count;                             //# file-table entries pointing to this
    public short flag;                              //0 = unused, 1 = used, ...
    public short[] direct = new short[directSize];  //direct pointers
    public short indirect;                          //an indirect pointer; 12th total data block,
                                                    //data referencing block locations on disk
    
    public Inode() {                                       //a default constructor
        this.length = 0;
        this.count = 0;
        this.flag = 1;
        for(int i = 0; i < directSize; i++)
            direct[i] = NOT_FOUND;                  //init to -1 by default, to flag as invalid ref
                                     
        this.indirect = NOT_FOUND;
    }
    
    

    public Inode(short iNumber) {                          //retrieving inode from disk
       
        if (iNumber < 0)
            return;

        int blkNumber = 1 + (iNumber / 16);
        int offset = (iNumber % 16) * iNodeSize;
        
        //create an empty block to popuplate
        byte[] b = new byte[Disk.blockSize];
        
        //read the block data from file
        SysLib.rawread(blkNumber, b);
        
        //deserialize the block length data
        this.length = SysLib.bytes2int(b, offset);
        offset += 4;

        //deserialize the count data
        this.count = SysLib.bytes2short(b, offset);
        offset += 2;

        //deserialize the flag data
        this.flag = SysLib.bytes2short(b, offset);
        offset += 2;
        
        //iterate over the direct links
        for(int i = 0; i < directSize; i++)
        {
            //deserialize the block pointer data
            direct[i] = SysLib.bytes2short(b, offset);
            offset += 2;
        }
        
        //deserialize the last, indirect pointer
        indirect = SysLib.bytes2short(b, offset);
    }
    
    

    public void toDisk(short iNumber) {                     //save to disk as the i-th inode
        //utility function that moves all current
        //data from memory to file 
        
        //input sanitation
        if(iNumber < 0)
            return;

        int blkNumber = 1 + (iNumber / 16);
        int offset = (iNumber % 16) * iNodeSize; //0;   


        //create an empty block to popuplate
        byte[] b = new byte[Disk.blockSize];

        //read the block from cache/memory to buffer
        SysLib.rawread(blkNumber, b);


        //serialize the length data
        SysLib.int2bytes(this.length, b, offset);
        offset += 4;
        
        //serialize the block count data
        SysLib.short2bytes(this.count, b, offset);
        offset += 2;

        //serialize the flag data
        SysLib.short2bytes(this.flag, b, offset);
        offset += 2; 


        //iterate over the linked list
        for(int i = 0; i < directSize; i++)
        {
            //serialize the block pointer data
            SysLib.short2bytes(this.direct[i], b,  offset);
            offset += 2;
        }


        //serialize the last, indirect pointer
        SysLib.short2bytes(this.indirect, b,  offset);
        offset += 2;


        //write the byte data to file
        SysLib.rawwrite(blkNumber, b);
    }
    

    
    public int findIndexBlock()
    {     
        return this.indirect; 
    }



    public int findTargetBlock(int offset)
    {
        //sanitize input 
        if(offset < 0) 
            return  NOT_FOUND;

        int blkNumber = offset/Disk.blockSize;
        //test if the offset is within the direct block
        if(blkNumber < directSize)
            return this.direct[blkNumber];


        //test if indirect is used
        if(this.indirect == NOT_FOUND)
            return NOT_FOUND;


        //block is in indirect; deserialize to find 
        byte[] b = new byte[Disk.blockSize];
        SysLib.rawread(this.indirect, b);
        return SysLib.bytes2short(b, (blkNumber - directSize) * 2) ;
    }



    public boolean registerIndexBlock(short iNumber)
    {
        //sanitize input
        if(iNumber < 0)
            return false;
        
        //test if the direct list is already used
        for(int i = 0; i < directSize; i++)
        {
            if(direct[i] == NOT_FOUND)
                return false;
        }

        //test if indirect is in use
        if(this.indirect > NOT_FOUND)
            return false;
        
        //register the indirect to the block number
        this.indirect = iNumber;

        //create an empty block 
        byte[] b = new byte[Disk.blockSize];

        //populate the indirect array
        for(int i = 0; i < (Disk.blockSize / 2); i++)
        {
            SysLib.short2bytes((short)NOT_FOUND, b, i * 2);
        }

        SysLib.rawwrite(iNumber, b);
        //return success
        return true;

    }


    
    public int registerTargetBlock(int offset, short iNumber)
    {
        //sanitize input
        if(iNumber < 0)
            return NOT_FOUND;
        
        //check if space is available
        int blkNumber = offset/Disk.blockSize;

        //test if block is in direct list 
        if(blkNumber < directSize)
        {
            //test if already in use
            if(direct[blkNumber] != NOT_FOUND)
                return ErrorBlockRegistered;
            
            //test if previous block is unused
            if(blkNumber > 0 && direct[blkNumber - 1] == NOT_FOUND)
                return ErrorPrecBlockUnused;

            //register the block
            direct[blkNumber] = iNumber;

            //return success
            return NoError;
        }


        //block is in indirect space
        //test if indirect is unused
        if(indirect == NOT_FOUND)
            return ErrorIndirectNull;
    
        //move to the indirect space
        int blockOffset =  blkNumber - directSize;

        byte[] b = new byte[Disk.blockSize];
        SysLib.rawread(this.indirect, b);


        int blkOffset = blkNumber - directSize;

        if(SysLib.bytes2short(b, blkOffset * 2) > 0) //NOT_FOUND)
        {
            return ErrorPrecBlockUnused;
        }


        SysLib.short2bytes(iNumber, b, blkOffset * 2);
        SysLib.rawwrite(indirect, b);
        return NoError;
    }

    

    public byte[] unregisterIndexBlock()
    {
        //test if indirect is populated
        if(this.indirect < 0)
            return null;


        //create a temp block
        byte[] b  = new byte[Disk.blockSize];

        //read the indirect block
        SysLib.rawread(this.indirect, b);

        //clear the localk indirect block
        this.indirect = NOT_FOUND;

        //return the temp block
        return b;
    }
}
