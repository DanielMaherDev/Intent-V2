# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.02**.

## Download

**Debug APK (v0.0.01)** — current published artifact:

https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.01-debug.apk

(Repo is at v0.0.02; a matching debug APK has not been published under `artifacts/` yet.)

## Build

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Scope

Evolving-intention foundation on a brand-new HOME launcher — not the full INTENT vision, and not a reuse of intent-app / `com.intent.coach`.

- **v0.0.01:** Spanish scripted E2E (explore → fact → proposal → approve → defer → restart)
- **v0.0.02:** Marathon second shape on the same model/screens (`Explore a marathon` on Home)

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions). Shape tag: `language` | `marathon` | `business`.
