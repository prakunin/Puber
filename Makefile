# Deploy Puber to an Android TV device over adb.
#
#   make deploy                          optimized non-debug dev build, install and launch
#   make run                             debug build, install and launch the dev app
#   make install FLAVOR=prod             install the prod flavor instead
#   make logs                            follow the app log
#   make DEVICE=192.168.1.106:5555 run   target another device
#
# The dev flavor installs as com.kino.puber.stage and lives next to a release
# build. The prod flavor shares com.kino.puber with official releases, and an
# install over one of those fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE:
# local builds are signed with app/debug.jks, releases with the release key.

# Local settings; see .env.example. Values here win over the defaults below,
# and a variable passed on the command line wins over both.
-include .env

DEVICE ?=
FLAVOR ?= dev
BUILD_TYPE ?= debug

ADB ?= $(shell command -v adb 2>/dev/null || echo /opt/homebrew/bin/adb)
GRADLE ?= ./gradlew

PACKAGE_dev := com.kino.puber.stage
PACKAGE_prod := com.kino.puber
PACKAGE = $(PACKAGE_$(FLAVOR))
ACTIVITY := com.kino.puber.MainActivity

capitalize = $(shell echo $(1) | awk '{print toupper(substr($$0,1,1)) substr($$0,2)}')
VARIANT = $(call capitalize,$(FLAVOR))$(call capitalize,$(BUILD_TYPE))
APK = app/build/outputs/apk/$(FLAVOR)/$(BUILD_TYPE)/app-$(FLAVOR)-$(BUILD_TYPE).apk

TARGET := $(ADB) -s $(DEVICE)

.DEFAULT_GOAL := help
.PHONY: help devices connect build deploy install run stop restart logs info uninstall check coverage require-device

help:
	@echo "Puber deploy targets (DEVICE=$(if $(DEVICE),$(DEVICE),<unset>) FLAVOR=$(FLAVOR) BUILD_TYPE=$(BUILD_TYPE) -> $(PACKAGE))"
	@echo
	@echo "  make devices     list adb devices"
	@echo "  make connect     connect to \$$DEVICE over the network"
	@echo "  make build       assemble$(VARIANT)"
	@echo "  make deploy      optimized non-debug dev build, install and launch"
	@echo "  make install     build and install, keeping app data"
	@echo "  make run         install and launch"
	@echo "  make restart     force-stop and launch again"
	@echo "  make stop        force-stop the app"
	@echo "  make logs        follow logcat for the app only"
	@echo "  make info        installed version on the device"
	@echo "  make uninstall   remove the app and its data"
	@echo "  make check       unit tests, detekt and android lint"
	@echo "  make coverage    unit-test coverage report"
	@echo "  make itest       instrumented tests on \$$DEVICE only, keeping its login"
	@echo "  make auth-save   save this device's KinoPub pairing"
	@echo "  make auth-restore  put a saved pairing back"

devices:
	$(ADB) devices -l

require-device:
	@if [ -z "$(DEVICE)" ]; then \
		echo "DEVICE is not set. Put it in .env (see .env.example) or pass DEVICE=<host:port>."; \
		echo "Run 'make devices' to list what adb sees."; \
		exit 1; \
	fi

connect: require-device
	@$(ADB) connect $(DEVICE) >/dev/null

build:
	$(GRADLE) assemble$(VARIANT)

install: connect build
	$(TARGET) install -r $(APK)

run: install
	$(TARGET) shell am start -n $(PACKAGE)/$(ACTIVITY)

deploy: FLAVOR := dev
deploy: BUILD_TYPE := deploy
deploy: run

stop: connect
	$(TARGET) shell am force-stop $(PACKAGE)

restart: stop
	$(TARGET) shell am start -n $(PACKAGE)/$(ACTIVITY)

logs: connect
	@pid=$$($(TARGET) shell pidof -s $(PACKAGE) | tr -d '\r'); \
	if [ -z "$$pid" ]; then echo "$(PACKAGE) is not running on $(DEVICE)"; exit 1; fi; \
	$(TARGET) logcat --pid=$$pid

info: connect
	@$(TARGET) shell dumpsys package $(PACKAGE) \
		| grep -E "versionName|versionCode|lastUpdateTime" \
		| head -3 \
		| grep . || echo "$(PACKAGE) is not installed on $(DEVICE)"

uninstall: connect
	$(TARGET) uninstall $(PACKAGE)

coverage:
	$(GRADLE) :app:koverHtmlReport$(VARIANT) :app:koverLog$(VARIANT)

check:
	$(GRADLE) test$(VARIANT)UnitTest :app:detektAll :app:lint$(VARIANT)

# Instrumented tests on one device, keeping its login.
#
# Two things go wrong when connected*AndroidTest is run plainly. It picks the
# devices itself and ignores ANDROID_SERIAL, so every attached television gets
# the test APKs; and it uninstalls the app when it finishes, taking the KinoPub
# pairing with it — which on a box with no display attached is not a step the
# user can simply repeat.
#
# So this runs against a private adb server that has been told about $(DEVICE)
# and nothing else, and asks AGP to leave the APKs installed. Disconnecting the
# other devices from the ordinary server is not enough: adb reconnects them by
# itself, and a run three minutes long is long enough for that to happen.
ITEST_ADB_PORT ?= 5038
ITEST_ADB = ANDROID_ADB_SERVER_PORT=$(ITEST_ADB_PORT) $(ADB)

.PHONY: itest auth-save auth-restore

itest: require-device build
	@$(ITEST_ADB) start-server >/dev/null 2>&1
	@$(ITEST_ADB) disconnect >/dev/null 2>&1 || true
	@$(ITEST_ADB) connect $(DEVICE) >/dev/null
	@echo "instrumented run sees:"; $(ITEST_ADB) devices | sed -n '2,$$p'
	ANDROID_ADB_SERVER_PORT=$(ITEST_ADB_PORT) $(GRADLE) :app:connected$(VARIANT)AndroidTest \
		-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
		$(if $(TESTS),-Pandroid.testInstrumentationRunnerArguments.class=$(TESTS),) \
		; status=$$?; $(ITEST_ADB) kill-server >/dev/null 2>&1; exit $$status

# The pairing itself, saved and put back. `make itest` is meant to keep it, but
# anything that uninstalls the app still drops it, and re-pairing needs both the
# account owner and a screen to read the code from.
AUTH_SNAPSHOT ?= .auth/$(PACKAGE).tar

AUTH_DIRS := shared_prefs files databases datastore no_backup

auth-save: connect
	@mkdir -p $(dir $(AUTH_SNAPSHOT))
	@present=$$($(TARGET) shell run-as $(PACKAGE) ls -1 /data/data/$(PACKAGE) \
		| tr -d '\r' | grep -x $(foreach d,$(AUTH_DIRS),-e $(d)) | tr '\n' ' '); \
	if [ -z "$$present" ]; then echo "$(PACKAGE) has nothing to save yet — pair it first"; exit 1; fi; \
	echo "saving: $$present"; \
	$(TARGET) exec-out run-as $(PACKAGE) tar cf - -C /data/data/$(PACKAGE) $$present > $(AUTH_SNAPSHOT)
	@echo "saved $$(wc -c < $(AUTH_SNAPSHOT) | tr -d ' ') bytes to $(AUTH_SNAPSHOT)"

# Pushed through the shell rather than piped into `adb shell`, which is free to
# translate line endings and would corrupt the archive.
auth-restore: connect
	@test -s $(AUTH_SNAPSHOT) || { echo "no snapshot at $(AUTH_SNAPSHOT); run 'make auth-save' while paired"; exit 1; }
	@$(TARGET) shell am force-stop $(PACKAGE)
	@$(TARGET) push $(AUTH_SNAPSHOT) /data/local/tmp/puber-auth.tar >/dev/null
	@$(TARGET) shell 'cat /data/local/tmp/puber-auth.tar | run-as $(PACKAGE) tar xf - -C /data/data/$(PACKAGE)'
	@$(TARGET) shell rm -f /data/local/tmp/puber-auth.tar
	@echo "restored $(AUTH_SNAPSHOT) to $(PACKAGE) on $(DEVICE)"
