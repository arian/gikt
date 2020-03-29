SOURCES=$(shell find src/main -name "*.kt")
TEST_SOURCES=$(shell find src/test -name "*.kt")

TEST_LIBS=\
	../gikt-lib/guava-28.2-jre.jar \
	../gikt-lib/jimfs-1.1.jar

TEST_LIBS_COMPILE=$(TEST_LIBS) \
	../gikt-lib/junit-jupiter-api-5.6.0.jar

TEST_CLASSPATH_COMPILE=$(shell echo $(TEST_LIBS_COMPILE) | tr ' ' ':')

TEST_CLASSPATH_RUN=$(shell echo $(TEST_LIBS) | tr ' ' ':')

gikt.jar: $(SOURCES)
	kotlinc $^ -include-runtime -jvm-target 1.8 -d gikt.jar

gikt-test.jar: gikt.jar $(TEST_SOURCES) $(TEST_LIBS_COMPILE)
	kotlinc $(TEST_SOURCES) -include-runtime -jvm-target 1.8 -classpath $(TEST_CLASSPATH_COMPILE):gikt.jar -d gikt-test.jar

run: gikt.jar
	java -jar gikt.jar $(ARGS)

commit: gikt.jar
	cat .git/COMMIT_MSG | \
	GIT_AUTHOR_EMAIL="stolwijk.arian@gmail.com" \
	GIT_AUTHOR_NAME=arian \
	java -jar gikt.jar commit

test: gikt-test.jar
	java -jar ../gikt-lib/junit-platform-console-standalone-1.6.1.jar \
		-classpath $(TEST_CLASSPATH_RUN):gikt.jar:gikt-test.jar \
		--exclude-engine=junit-vintage \
		--fail-if-no-tests \
		--scan-classpath \
		--include-package=com.github.arian.gikt

clean:
	rm -f gikt.jar
	rm -f gikt-test.jar

.PHONY: clean test run
