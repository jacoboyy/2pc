all: Server.class UserNode.class

%.class: %.java
	javac $<

.PHONY: format
format:
	find *.java | sed 's| |\\ |g' | xargs clang-format -style=google -i

.PHONY: submit
submit:
	tar cvzf ../mysolution.tgz Makefile *.java *.pdf

clean:
	rm -f *.class
	rm -f ../mysolution.tgz
