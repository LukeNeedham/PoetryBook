# Notes for Claude

## Logging, crashes and debug screens come from the shared library

`com.lukeneedham:common` (source: `LukeNeedham/ci-workflows`, `common/`; docs: `docs/using-the-library.md` there) provides the logger, crash handler and debug pages. Do not write our own.

- Log with `Logger.d/i/w/e(message, ...)` (`com.lukeneedham.common.logger.Logger`). Never use `Log.*` or `println`.
- `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))` is the first thing in `App.onCreate`.
- `MainActivity` wraps the UI in `ProvideDebugSettings`, the library's single provider of debug composition locals. Keep it wrapped even if a screen reads no setting yet, so a setting added to the library later reaches every screen. New animations should pass their duration through `LocalAnimationSpeed.current.scale(ms)` so the debug speed setting applies. The existing ones do not yet: the screen transitions (`ScreenTransitionMillis` in `AppNavigation.kt`, which `BookClearDelayMillis` is derived from) and the page turn (`PageTurnState.settle`) use fixed durations, so the speed setting does not affect them.
- The `Debug`, `Logs` and `Crashes` routes (`navigation/Destinations.kt`) are registered only when `BuildConfig.DEBUG`; `DebugButton` on the shelf opens them. The shared `DebugPage` shows only the library's own things and has no slot for app-specific tools; the app has none yet. When it gets some, make an app debug page for them with a button that opens the library's page.
- The library comes from the Maven repo in `settings.gradle.kts`; change its version in `gradle/libs.versions.toml`.
