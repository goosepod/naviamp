GRADLE ?= ./gradlew
GRADLEW_BAT ?= ./gradlew.bat

ANDROID_HOME ?= /Users/jbmcmichael/Library/Android/sdk
GRADLE_COMMON = --configure-on-demand
MACOS_DESKTOP_PROPS = -Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false
WINDOWS_DESKTOP_PROPS = -Pnaviamp.bass.platform=windows-x64
LINUX_DESKTOP_PROPS = -Pnaviamp.bass.platform=linux-x64

.PHONY: help
help:
	@printf "Naviamp build shortcuts\n\n"
	@printf "macOS:\n"
	@printf "  make macos-test          Build, stage, and open build/local-test/Naviamp.app\n"
	@printf "  make macos-standalone    Build release zip at apps/desktop/build/compose/distributions\n"
	@printf "  make macos-stage         Build and stage build/release/Naviamp.app\n"
	@printf "  make macos-installer     Build macOS DMG installer\n\n"
	@printf "Windows, run on a Windows runner or shell:\n"
	@printf "  make windows-test        Build and stage the Windows test app\n"
	@printf "  make windows-standalone  Build Windows release zip\n"
	@printf "  make windows-installer   Build Windows MSI/EXE installer\n\n"
	@printf "Linux, run on a Linux runner or shell:\n"
	@printf "  make linux-test          Build and stage the Linux test app\n"
	@printf "  make linux-standalone    Build Linux release zip\n"
	@printf "  make linux-installer     Build Linux DEB/RPM packages\n\n"
	@printf "Android:\n"
	@printf "  make android-debug       Build debug APK\n"
	@printf "  make android-release     Build release APK/AAB tasks configured by Gradle\n\n"
	@printf "  make android-play-release Build signed release AAB for Google Play\n\n"
	@printf "Android Auto DHU:\n"
	@printf "  make android-auto-dhu    Install debug APK, start head unit server, and launch DHU\n"
	@printf "  make android-auto-start  Start head unit server and launch DHU without reinstalling\n"
	@printf "  make android-auto-logs   Follow Naviamp Android Auto logs\n"
	@printf "  make android-auto-status Show connected device and package state\n\n"
	@printf "Verification:\n"
	@printf "  make version-check       Validate VERSION and VERSION_CODE\n"
	@printf "  make bump-version PART=patch|minor|major\n"
	@printf "  make clean               Run Gradle clean\n"
	@printf "  make clean-generated     Run Gradle clean and remove root generated staging outputs\n"
	@printf "  make desktop-test        Run desktop tests\n"

.PHONY: version-check
version-check:
	scripts/validate-version.sh

.PHONY: bump-version
bump-version:
	scripts/bump-version.sh $(or $(PART),patch)

.PHONY: clean
clean:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) clean

.PHONY: clean-generated
clean-generated: clean
	rm -rf build

.PHONY: macos-test
macos-test:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) $(MACOS_DESKTOP_PROPS) :apps:desktop:stageLocalTestApp
	-pkill -f "$(CURDIR)/build/local-test/Naviamp.app/Contents/MacOS/Naviamp"
	open -n build/local-test/Naviamp.app

.PHONY: macos-stage
macos-stage:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) $(MACOS_DESKTOP_PROPS) :apps:desktop:stageReleaseApp

.PHONY: macos-standalone macos-release
macos-standalone macos-release:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) $(MACOS_DESKTOP_PROPS) :apps:desktop:packageReleaseDistributable

.PHONY: macos-installer
macos-installer:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) $(MACOS_DESKTOP_PROPS) :apps:desktop:packageReleaseDistributionForCurrentOS

.PHONY: windows-test
windows-test:
	@if uname -s | grep -Eq 'MINGW|MSYS|CYGWIN|Windows'; then \
		$(GRADLEW_BAT) $(GRADLE_COMMON) "$(WINDOWS_DESKTOP_PROPS)" :apps:desktop:stageLocalTestApp; \
	else \
		printf "windows-test must run on Windows so jpackage can create a Windows app image.\n"; \
		exit 1; \
	fi

.PHONY: windows-standalone windows-release
windows-standalone windows-release:
	@if uname -s | grep -Eq 'MINGW|MSYS|CYGWIN|Windows'; then \
		$(GRADLEW_BAT) $(GRADLE_COMMON) "$(WINDOWS_DESKTOP_PROPS)" :apps:desktop:packageReleaseDistributable; \
	else \
		printf "windows-standalone must run on Windows so jpackage can create a Windows app image.\n"; \
		exit 1; \
	fi

.PHONY: windows-installer
windows-installer:
	@if uname -s | grep -Eq 'MINGW|MSYS|CYGWIN|Windows'; then \
		if ! command -v candle.exe >/dev/null 2>&1 || ! command -v light.exe >/dev/null 2>&1; then \
			printf "windows-installer requires WiX Toolset 3.x on PATH so jpackage can create MSI/EXE installers.\n"; \
			exit 1; \
		fi; \
		$(GRADLEW_BAT) $(GRADLE_COMMON) "$(WINDOWS_DESKTOP_PROPS)" :apps:desktop:packageReleaseDistributionForCurrentOS; \
	else \
		printf "windows-installer must run on Windows so jpackage can create a Windows installer.\n"; \
		exit 1; \
	fi

.PHONY: linux-test
linux-test:
	@if uname -s | grep -Eq 'Linux'; then \
		$(GRADLE) $(GRADLE_COMMON) $(LINUX_DESKTOP_PROPS) :apps:desktop:stageLocalTestApp; \
	else \
		printf "linux-test must run on Linux so jpackage can create a Linux app image.\n"; \
		exit 1; \
	fi

.PHONY: linux-standalone linux-release
linux-standalone linux-release:
	@if uname -s | grep -Eq 'Linux'; then \
		$(GRADLE) $(GRADLE_COMMON) $(LINUX_DESKTOP_PROPS) :apps:desktop:packageReleaseDistributable; \
	else \
		printf "linux-standalone must run on Linux so jpackage can create a Linux app image.\n"; \
		exit 1; \
	fi

.PHONY: linux-installer
linux-installer:
	@if uname -s | grep -Eq 'Linux'; then \
		$(GRADLE) $(GRADLE_COMMON) $(LINUX_DESKTOP_PROPS) :apps:desktop:packageReleaseDistributionForCurrentOS; \
	else \
		printf "linux-installer must run on Linux so jpackage can create Linux packages.\n"; \
		exit 1; \
	fi

.PHONY: android-debug
android-debug:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) :apps:android:assembleDebug

.PHONY: android-release
android-release:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) :apps:android:assembleRelease :apps:android:bundleRelease

.PHONY: android-play-release
android-play-release:
	scripts/require-android-signing.sh
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) $(GRADLE_COMMON) :apps:android:bundleRelease

.PHONY: android-auto-dhu
android-auto-dhu:
	ANDROID_HOME="$(ANDROID_HOME)" scripts/android-auto-dhu.sh run

.PHONY: android-auto-start
android-auto-start:
	ANDROID_HOME="$(ANDROID_HOME)" scripts/android-auto-dhu.sh start

.PHONY: android-auto-logs
android-auto-logs:
	ANDROID_HOME="$(ANDROID_HOME)" scripts/android-auto-dhu.sh logs

.PHONY: android-auto-status
android-auto-status:
	ANDROID_HOME="$(ANDROID_HOME)" scripts/android-auto-dhu.sh status

.PHONY: android-auto-stop
android-auto-stop:
	ANDROID_HOME="$(ANDROID_HOME)" scripts/android-auto-dhu.sh stop

.PHONY: desktop-test
desktop-test:
	ANDROID_HOME="$(ANDROID_HOME)" $(GRADLE) desktopTest
