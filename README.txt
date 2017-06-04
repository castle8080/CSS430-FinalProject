
Buliding and Running:
--------------------------------------

To compile:
  make

To run
  make run

After running testing can be done by either:

  1. l Test5
     This will run the provided test cases.

  2. l FSShell
     This will run an interactive test shell.
     See FSShell.java for commands.

Files:
--------------------------------------

Makefile
  This is the Makefile used for building and running the code.

lib/threados.jar
  This contains the binary classes for ThreadOS

src/
  The source directory contains sources needed for the FileSystem


src/Directory.java
src/FileMode.java
src/FileSystemException.java
src/FileSystem.java
src/FileTable.java
src/Inode.java
src/Kernel.java
src/Seek.java
src/SuperBlock.java
  The files listed above are the main sources for the
  file system.

src/Scheduler.java
  The scheduler was provided by the instructor and
  it hooks thread creation and exit to handle
  file table entries.

src/Test5.java
src/FSShell.java
  The following files are for testing.
  Test5 was provided by the instructor and
  FSShell is an interactive test tool.

