@echo off
md classes
echo "Compiling source....."
javac -g -cp classes;lib\threados.jar -d classes src\*.java
