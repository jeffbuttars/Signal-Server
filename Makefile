
.PHONY: clean start status restart kill stop
.DEFAULT: all

PID_FILE := signalserver.pid
SIGNAL_SERVER_CFG_YML ?= config/signalserver.yml
SS_VER := 1.12
TARGET := target/TextSecureServer-$(SS_VER).jar

JAVA_SRC_FILES = $(shell find src/ -type f -name '*.java')

$(TARGET): $(JAVA_SRC_FILES)
	mvn -e package

all: target/TextSecureServer-$(SS_VER).jar

status:
	@if [ ! -f $(PID_FILE) ]; then \
		echo "No pid file, Signal Server should not be running"; \
	else \
		echo "Signal Server running $$(cat $(PID_FILE))..."; \
	fi
	ps aux | grep java | grep -i textsecureserver
	sudo netstat -tulpn | grep java

start: $(TARGET)
	(java -jar $(TARGET) server \
		config/signalserver.yml > signalserver.log) & echo $$! > $(PID_FILE)
	echo "Started Signal Server $$(cat signalserver.pid)"; sleep 4; \
	$(MAKE) status

stop:
	-kill $$(cat $(PID_FILE)) && rm -f $(PID_FILE)
	ps aux | grep -i signal-server
	$(MAKE) status

kill:
	-kill -9 $$(cat $(PID_FILE)) && rm -f $(PID_FILE)
	ps aux | grep -i signal-server
	$(MAKE) status

restart:
	$(MAKE) stop
	$(MAKE) start

clean:
	rm -fr target
	rm -f *.pid
