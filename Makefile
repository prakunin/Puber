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
.PHONY: help devices connect build deploy install run stop restart logs info uninstall check require-device

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
	@echo "  make check       unit tests and detekt"

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

check:
	$(GRADLE) test$(VARIANT)UnitTest :app:detektAll
