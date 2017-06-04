

//the "/" root directory maintains each file in a different directory entry that cotnains its file name

public class Directory
{
    private static final int maxChars = 30;                           //max characters of each file name
    private static final int NOT_FOUND = -1;
    //directory entries
    private int fsizes[];                                       //each element stores a different file size 
    private char fnames[][];                                    //each element stores a different file name


    public Directory(int maxNumber) {                           //directory constructor
        fsizes = new int[maxNumber];                            //maxNumber = max files
        for(int i = 0; i < maxNumber; i++)
            fsizes[i] = 0;                                      //all file size init to 0
        fnames = new char[maxNumber][maxChars];
        String root = "/";                                      //entry(inode) 0 is "/"
        fsizes[0] = root.length();                              //fsizes[0] is the size of "/"
        root.getChars(0, fsizes[0], fnames[0], 0);              //fnames[0] includes "/"
    }
    
    public void bytes2directory(byte data[]) { 
        //assumes data[] receives directory information from disk
        //inits the directory instance with this data[]
        
        //consider input sanitation
        if(data == null) // || data[0] == null) 
        {
            //reconsider this implementation; 
            //it might be ok that data[] is null/empty, at which point
            //fsize and fnames would also be empty
            SysLib.cerr("Argument Error: Directory.java(data[]); data == null\n");
            return;
        }
        
        int offset = 0;
        for(int i = 0; i < fsizes.length; i++)
        {
            fsizes[i] = SysLib.bytes2int(data, offset);
            offset += 4;
        }
        
        
        for(int i = 0; i < fnames.length; i++)
        {
            String fn = new String(data, offset, maxChars * 2);
            for(int j = 0; j < fsizes[i]; j++)
            {
                fnames[i][j] = fn.charAt(j);
            }
            offset += maxChars * 2;
        }
    }
    
    public byte[] directory2bytes() { 
        //converts and returns directory information into a plain byte array
        //this byte array will be written back to disk
        //note: only meaningful directory information should be converted
        //into bytes
        
        //used during a sync call
        //consider shortcircuits 
        
        int fsizeByteCount = fsizes.length * 4;
        int fnameByteCount = fsizes.length * maxChars * 2;
        
        byte[] b = new byte[fsizeByteCount + fnameByteCount];

        int offset = 0;
        for(int i = 0; i < fsizes.length; i++)
        {
            SysLib.int2bytes(fsizes[i], b, offset);
            offset += 4;
        }


        for(int i = 0; i < fnames.length; i++)
        {
            String fn = new String(fnames[i], 0, fsizes[i]);
            byte[] fnBytes = fn.getBytes();
            
            for(int j = 0; j < fnBytes.length; j++)
            {
                b[offset] = fnBytes[j];
                offset++;
            }
            offset += maxChars * 2;
        }
        
        return b;
    }
    
    public short ialloc(String filename) { 
        //filename is the one of a file to be created.
        //allocates a new inode number for this filename
        
        //input sanitation 
        if(filename == null || filename.isEmpty())
            return NOT_FOUND;
        
        
        short output = NOT_FOUND;
        for(int i = 1; i < fsizes.length; i++)
        {
            if(fsizes[i] == 0)
            {
                output = (short)i;
                fsizes[i] = Math.min(filename.length(), maxChars);

                for(int j = 0; j < fsizes[i]; j++)
                    fnames[i][j] = filename.charAt(j);
                break;
            }
        }
        return output;
    }
    
    public boolean ifree(short iNumber) { 
        //deallocates this inumber (inode number)
        //the corresponding file will be deallocated
        
        //input sanitation
        if(fsizes[iNumber] < 0 || iNumber > fsizes.length - 1)
            return false;
        
        
            fsizes[iNumber] = 0;
            //fnames[iNumber] = new char[maxChars];
            return true;
    }
    
    public short namei(String filename) { 
        //returns the inumber corresponding to this filename
        
        //input sanitation 
        if(filename == null || filename.isEmpty())
            return NOT_FOUND;

        int fileLength = filename.length();
        for(int i = 0; i < fsizes.length; i++)
        {
            if(fsizes[i] == fileLength)
            {
                String fn = new String(fnames[i], 0, fsizes[i]);

                if(filename.compareToIgnoreCase(fn) == 0)
                    return (short)i;
            }
        }
        return NOT_FOUND;
    }
}