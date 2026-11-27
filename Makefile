# Shortcuts for building and checking the app.
# Locally every command runs inside Docker. On CI (no Docker wrapper) use: make <target> RUN=
# App parameters (APP_NAME, APP_ID, APP_URL, APP_VERSION_NAME, APP_VERSION_CODE) are read from the
# environment, so:  APP_URL=https://example.com make debug

RUN    ?= docker compose run --rm dev
# docker-compose.yml runs the container as the host user; bash's UID and GID are not exported, so do it here.
export UID := $(shell id -u)
export GID := $(shell id -g)
GRADLE  = $(RUN) ./gradlew

# The debug APK is copied to $(DIST)/<app-name>-<date>.apk (name from APP_NAME, then app.properties).
STAMP ?= $(shell date +%Y%m%d-%H%M)
DIST  ?= dist
prop  = $(shell sed -n 's/^$(1)=//p' app.properties)
slug  = $(shell printf '%s' '$(1)' | sed 's/[^A-Za-z0-9]\+/-/g; s/^-//; s/-$$//' | tr 'A-Z' 'a-z')
app_name = $(or $(APP_NAME),$(call prop,APP_NAME),app)
copy_apk = mkdir -p $(DIST) && cp app/build/outputs/apk/debug/app-debug.apk "$(DIST)/$(call slug,$(app_name))-$(STAMP).apk"

.DEFAULT_GOAL := help
.PHONY: help image check r8 packcheck test lint debug install release adb-devices clean shell

help: ## List the available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-12s %s\n", $$1, $$2}'

image: ## Build the Docker dev image (once, and after Dockerfile changes)
	docker compose build

check: ## Standard check: unit tests, content packs (packcheck) and lint
	$(GRADLE) :core:test :tools:packcheck:test :tools:packcheck:run testDebugUnitTest lint

r8: ## Run R8 minification of the release variant (no signing needed)
	$(GRADLE) :app:minifyReleaseWithR8

packcheck: ## Validate content packs and replay every mission solution
	$(GRADLE) :tools:packcheck:run

test: ## Unit tests only
	$(GRADLE) :core:test :tools:packcheck:test testDebugUnitTest

lint: ## Android lint only
	$(GRADLE) lint

debug: ## Build the debug APK into dist/ as <app>-<date>.apk
	$(GRADLE) assembleDebug
	$(call copy_apk)

install: ## Build and install the debug APK on a device/emulator
	$(GRADLE) assembleDebug installDebug

release: ## Build the signed release APK and AAB (needs signing, see README)
	$(GRADLE) assembleRelease bundleRelease

adb-devices: ## List devices seen by adb inside the container
	$(RUN) adb devices

clean: ## Remove build outputs (and the APKs in dist/)
	$(GRADLE) clean
	rm -f $(DIST)/*.apk

shell: ## Open a shell in the dev container
	$(RUN) bash
