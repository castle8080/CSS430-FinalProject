@echo off
call compile
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000 -cp classes;lib\threados.jar Boot
