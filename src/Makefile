all: Server.class UserNode.class

%.class: %.java
	javac $<

.PHONY: format
format:
	find *.java | sed 's| |\\ |g' | xargs clang-format -style=google -i

.PHONY: submit
submit:
	tar cvzf ../mysolution.tgz Coordinator.java CoordinatorEntry.java Decision.java Makefile MessageBody.java Server.java Slave.java SlaveEntry.java Stage.java UserNode.java

clean:
	rm -f *.class
	rm -f ../mysolution.tgz
