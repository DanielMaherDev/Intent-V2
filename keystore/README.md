# Sideload signing key

`intent-sideload.jks` is a **committed** development/sideload keystore for Intent-V2 APKs published under `artifacts/`.

## Why it exists

Cloud agents and local machines each generate a different `~/.android/debug.keystore`. APKs signed with mismatched keys cannot update the same `applicationId` via Android’s native Package Installer (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, often shown only as “App not installed”). Tools like ApkMirror Installer often uninstall-and-reinstall, which masks the failure.

## Usage

Configured in `app/build.gradle.kts` as `signingConfigs.sideload` for `debug` and `release`. Password/alias: `intent-sideload`.

## Security

This key is for **sideload continuity only** — not Play Store release. Treat it as public; rotate only if you accept forcing every device to uninstall first.

## Debuggable

Published `:app:assembleDebug` artifacts set `isDebuggable = false` so the system Package Installer accepts them. Use Gradle build type `engineering` when you need a debugger attached.

