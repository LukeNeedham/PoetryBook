# PoetryBook

An Android poetry app.

- Kotlin + Jetpack Compose (Material 3)
- Single activity (`MainActivity`), navigation with [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- CI from [LukeNeedham/ci-workflows](https://github.com/LukeNeedham/ci-workflows): every PR gets a debug APK link; the *Release* workflow publishes signed releases

## Build

```
./gradlew assembleDebug
```

Needs JDK 17+ and the Android SDK. The release build applies a Gradle script from `ci-workflows` by URL, so the first build needs network.

## Releases

See the [setup guide](https://github.com/LukeNeedham/ci-workflows/blob/main/docs/setup.md#3-permanent-releases-optional). The four `RELEASE_KEYSTORE_*` / `RELEASE_KEY_*` repo secrets must be added before running *Release*.
