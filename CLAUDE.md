# Notes for Claude

## Logging, crashes and debug screens come from the shared library

`com.lukeneedham:common` (source: `LukeNeedham/ci-workflows`, `common/`; docs: `docs/using-the-library.md` there) provides the logger, crash handler and debug pages. Do not write our own.

- Log with `Logger.d/i/w/e(message, ...)` (`com.lukeneedham.common.logger.Logger`). Never use `Log.*` or `println`.
- `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))` is the first thing in `App.onCreate`.
- `MainActivity` wraps the UI in `ProvideDebugSettings`, the library's single provider of debug composition locals. Keep it wrapped even if a screen reads no setting yet, so a setting added to the library later reaches every screen. Every animation with an explicit duration passes it through `LocalAnimationSpeed.current.scale(ms)` so the debug speed setting applies: the screen transitions (`ScreenTransitionMillis` in `AppNavigation.kt`, with `BookClearDelayMillis` derived from the scaled value so a closed book outlasts its slide) and the page turn (`PageTurnState.settle`, which gets the speed from `rememberPageTurnState`). Give any new animation an explicit duration and pass it through `scale`. Animations that use the default spring (`ContentsSheet`'s `AnimatedVisibility`, `Modifier.animateItem()`) are not affected by the setting.
- The `Debug`, `Logs` and `Crashes` routes (`navigation/Destinations.kt`) are registered only when `BuildConfig.DEBUG`; the "Debug" row on the Settings screen (`ui/settings/SettingsScreen.kt`, shown only in debug builds) opens them. The shared `DebugPage` shows only the library's own things and has no slot for app-specific tools; the app has none yet. When it gets some, make an app debug page for them with a button that opens the library's page.
- The library comes from the Maven repo in `settings.gradle.kts`; change its version in `gradle/libs.versions.toml`.

Before starting work, check that `.claude/skills/lukeneedham-conventions` matches `skills/lukeneedham-conventions` on `main` of `LukeNeedham/ci-workflows`; if not, copy it over and include that in this work's PR. See that repo's `docs/skill.md`.
