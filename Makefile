
all: build

clean:
	rm -rf classes

build:
	mkdir -p classes	
	javac -g -cp classes:lib/threados.jar -d classes src/*.java  

run: build
	java -cp classes:lib/threados.jar Boot

run_original:
	java -cp lib/threados.jar Boot

debug: build
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000 -cp classes:lib/threados.jar Boot

