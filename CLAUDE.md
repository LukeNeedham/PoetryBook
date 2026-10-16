# Notes for Claude

## Logging, crashes and debug screens come from the shared library

`com.lukeneedham:common` (source: `LukeNeedham/ci-workflows`, `common/`; docs: `docs/using-the-library.md` there) provides the logger, crash handler and debug pages. Do not write our own.

- Log with `Logger.d/i/w/e(message, ...)` (`com.lukeneedham.common.logger.Logger`). Never use `Log.*` or `println`.
- `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))` is the first thing in `App.onCreate`.
- `MainActivity` wraps the UI in `ProvideAnimationSpeed`; pass animation durations through `LocalAnimationSpeed.current.scale(ms)` so the debug speed setting applies.
- The `Debug`, `Logs` and `Crashes` routes (`navigation/Destinations.kt`) are registered only when `BuildConfig.DEBUG`; `DebugButton` on the shelf opens them. App-specific debug tools go in `DebugPage`'s `extraContent`.
- The library comes from the Maven repo in `settings.gradle.kts`; change its version in `gradle/libs.versions.toml`.
