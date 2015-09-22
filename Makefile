GS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	User.java \
	Server.java \
	Client.java \
	Message.java \
	SessionDetails.java



default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class

