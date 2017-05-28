# CSS430-FinalProject

## Testing

The basic tests should be run using:

	> l Test5

There is an interactive tester as well though, which has a number of commands:

	> l FSShell
	fs> dump
	fs [FileSystem@63d4f9bc] FileSystem
	    superBlock [SuperBlock@527db99c] SuperBlock
		totalBlocks [1000] Integer
		inodeBlocks [10] Integer
		freeList [2] Integer
	    root [Directory@136c6383] Directory
		fsizes [[I@6e91132b] int[]
		    fsizes[0] [1] Integer
		    fsizes[1] [0] Integer
		fnames [[[C@28a793a0] char[][]
		    fnames[0] [/] char[]
		    fnames[1] [] char[]
		    fnames[2] [] char[]
		    fnames[3] [] char[]
		    fnames[4] [] char[]
		    fnames[5] [] char[]
		    fnames[6] [] char[]
		    fnames[7] [] char[]
		    fnames[8] [] char[]
		    fnames[9] [] char[]
	    fileTable [FileTable@7bc3ff3a] FileTable
		table [[]] Vector
	fs> write foo hi_there
	fs> size foo
	Size: 8 fd:3
	fs> read foo
	Content:
	hi_there
	fs> dump   
	fs [FileSystem@63d4f9bc] FileSystem
	    superBlock [SuperBlock@527db99c] SuperBlock
		totalBlocks [1000] Integer
		inodeBlocks [10] Integer
		freeList [3] Integer
	    root [Directory@136c6383] Directory
		fsizes [[I@6e91132b] int[]
		    fsizes[0] [1] Integer
		    fsizes[2] [0] Integer
		fnames [[[C@28a793a0] char[][]
		    fnames[0] [/] char[]
		    fnames[1] [foo] char[]
		    fnames[2] [] char[]
		    fnames[3] [] char[]
		    fnames[4] [] char[]
		    fnames[5] [] char[]
		    fnames[6] [] char[]
		    fnames[7] [] char[]
		    fnames[8] [] char[]
		    fnames[9] [] char[]
	    fileTable [FileTable@7bc3ff3a] FileTable
		table [[]] Vector
	fs> exit
	Exiting FSShell.
	-->q
	q
