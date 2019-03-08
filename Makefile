JFLAGS = -d build
JC = javac

default: classes

classes: 
	mkdir -p build
	$(JC) $(JFLAGS) src/*.java

clean:
	$(RM) -r build