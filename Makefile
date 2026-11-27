# Shortcuts for building and checking the app.
# Locally every command runs inside Docker. On CI (no Docker wrapper) use: make <target> RUN=
# App parameters (APP_NAME, APP_ID, APP_URL, APP_VERSION_NAME, APP_VERSION_CODE) are read from the
# environment, so:  APP_URL=https://example.com make debug

RUN    ?= docker compose run --rm dev
# docker-compose.yml runs the container as the host user; bash's UID and GID are not exported, so do it here.
export UID := $(shell id -u)
export GID := $(shell id -g)
GRADLE  = $(RUN) ./gradlew

# The debug APK is copied to $(DIST)/<app-name>-<version>-<YYYYMMDDHHmm>.apk (name from APP_NAME, then app.properties).
ifndef STAMP
STAMP := $(shell date +%Y%m%d%H%M)
endif
DIST        ?= dist
# Dev builds: versionName is <version>-<stamp> (stamp: YYYYMMDDHHmm). The version is APP_VERSION_NAME (environment,
# then app.properties, then the latest CHANGELOG.md release).
# versionCode is the yyMMddHH part of the stamp, because the full stamp does not fit Android's 32-bit versionCode. Release builds are not affected.
H           := \#
DEV_BASE     = $(or $(APP_VERSION_NAME),$(call prop,APP_VERSION_NAME),$(shell sed -n 's/^$(H)$(H) \[\([0-9][^]]*\)\].*/\1/p' CHANGELOG.md | head -n1),0.1.0)
DEV_NAME     = $(DEV_BASE)-$(STAMP)
DEV_CODE     = $(shell printf '%s' '$(STAMP)' | cut -c3-10)
DEV_VERSION  = -PAPP_VERSION_CODE=$(DEV_CODE) -PAPP_VERSION_NAME="$(DEV_NAME)"
prop  = $(shell sed -n 's/^$(1)=//p' app.properties)
slug  = $(shell printf '%s' '$(1)' | sed 's/[^A-Za-z0-9]\+/-/g; s/^-//; s/-$$//' | tr 'A-Z' 'a-z')
app_name = $(or $(APP_NAME),$(call prop,APP_NAME),app)
apk = $(DIST)/$(call slug,$(app_name))-$(DEV_NAME).apk
copy_apk = mkdir -p $(DIST) && cp app/build/outputs/apk/debug/app-debug.apk "$(apk)" && echo "-> $(apk)"

.DEFAULT_GOAL := help
.PHONY: help image check r8 packcheck test lint dev debug install release adb-devices clean shell

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

dev: ## Build the debug APK into dist/ as <app>-<version>-<YYYYMMDDHHmm>.apk
	$(GRADLE) $(DEV_VERSION) assembleDebug
	$(call copy_apk)

debug: dev ## Alias of dev

install: ## Build and install the debug APK on a device/emulator
	$(GRADLE) $(DEV_VERSION) assembleDebug installDebug

release: ## Build the signed release APK and AAB (needs signing, see README)
	$(GRADLE) assembleRelease bundleRelease

adb-devices: ## List devices seen by adb inside the container
	$(RUN) adb devices

clean: ## Remove build outputs (and the APKs in dist/)
	$(GRADLE) clean
	rm -f $(DIST)/*.apk

shell: ## Open a shell in the dev container
	$(RUN) bash
