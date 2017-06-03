import java.util.*;

public class FileTable {


   private Vector table;         // the actual entity of this file table
   private Directory dir;        // the root directory 


   public FileTable( Directory directory ) { // constructor
      table = new Vector( );     // instantiate a file (structure) table
      dir = directory;           // receive a reference to the Director
   }                             // from the file system

	//flags; will need to match flags in Inode.java
	public final static int UNUSED = 0;
	public final static int USED = 1;
	public final static int READ = 2;
	public final static int WRITE = 3;


   // major public methods
   public synchronized FileTableEntry falloc( String filename, String mode ) {
      //--allocate a new file (structure) table entry for this file name
	FileTableEntry e;

	//temp variables
	short inumber = -1;
	Inode inode = null;

      //--allocate/retrieve and register the corresponding inode using dir
	while(true)
	{

		//get inumber by checking directory
		inumber = dir.namei(filename); //search for the filename

		if(inumber >= 0)	//file is in memory; mapped in directory
		{
			//get iNode
			inode = new Inode(inumber);

			//mode r; want to read
			if(mode.equals("r"))
			{
				//if flag is read, used, or unused, read the file
				if(inode.flag == READ || inode.flag == USED
					|| inode.flag == UNUSED)
				{
					inode.flag = READ; //thread is reading file
					break;
				}

				//some thread is writing to the file; wait
				else	
				{
					try
					{
						wait();
					} catch (InterruptedException z){}
					break;
				}
			}

			else	//mode w, w+, or a; request some kind of write
			{
				//if flag is used or unused, write to the file
				if(inode.flag == USED || inode.flag == UNUSED)
				{
					inode.flag = WRITE; //no wait, start writing
					break;
				}

				//another thread is using the file; wait
				else	
				{
					try
					{
						wait();
					} catch (InterruptedException z){}
					break;
				}
			}
		}

		//file not in memory; not mapped in directory
		else
		{
			if(mode != "r")
			{
				inumber = dir.ialloc(filename);	//get an iNumber from the directory
				inode = new Inode();	//allocate new Inode
				break;
			}
			else	//trying to read a file that doesn't exist.
				return null;
		}
	}

      //--increment this inode's count
	inode.count++;

      //--immediately write back this inode to the disk
	inode.toDisk(inumber);

      //--return a reference to this file (structure) table entry
	e = new FileTableEntry(inode, inumber, mode);
	table.addElement(e);	//add entry to table
	return e;		//return entry
   }


   public synchronized boolean ffree( FileTableEntry e ) {
      // receive a file table entry reference
      // save the corresponding inode to the disk
      // free this file table entry.
      // return true if this file table entry found in my table

	//if element is in table, remove, etc.
	if( table.removeElement(e) )	//try to remove the entry from the table
	{
		e.inode.count--;

		//if there is a request to use, notify
		if(e.inode.flag == READ || e.inode.flag == WRITE)
		{
			notify();
		}

		// save the corresponding inode to the disk
		e.inode.flag = USED;
		e.inode.toDisk(e.iNumber);

		return true;
	}
	else	//element not in table, cannot free
		return false;
   }


   public synchronized boolean fempty( ) {
      return table.isEmpty( );  // return if table is empty 
   }                            // should be called before starting a format
}

