BUILD = ./gradlew
JAVA = java

.PHONY: all

all: run

build:
	$(BUILD) build

run:
	$(BUILD) build
	$(JAVA) -jar build/libs/httpsocketclient.jar "$(args)$"
