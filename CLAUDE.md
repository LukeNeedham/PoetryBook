# Notes for Claude

## Logging, crashes and debug screens come from the shared library

`com.lukeneedham:common` (source: `LukeNeedham/ci-workflows`, `common/`; docs: `docs/using-the-library.md` there) provides the logger, crash handler and debug pages. Do not write our own.

- Log with `Logger.d/i/w/e(message, ...)` (`com.lukeneedham.common.logger.Logger`). Never use `Log.*` or `println`.
- `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))` is the first thing in `App.onCreate`.
- `MainActivity` wraps the UI in `ProvideDebugSettings`, the library's single provider of debug composition locals. Keep it wrapped even if a screen reads no setting yet, so a setting added to the library later reaches every screen. Every animation with an explicit duration passes it through `LocalAnimationSpeed.current.scale(ms)` so the debug speed setting applies: the screen transitions (`ScreenTransitionMillis` in `AppNavigation.kt`, with `BookClearDelayMillis` derived from the scaled value so a closed book outlasts its slide) and the page turn (`PageTurnState.settle`, which gets the speed from `rememberPageTurnState`). Give any new animation an explicit duration and pass it through `scale`. Animations that use the default spring (`ContentsSheet`'s `AnimatedVisibility`, `Modifier.animateItem()`) are not affected by the setting.
- The `DebugNavKey`, `LogsNavKey` and `CrashesNavKey` routes (`navigation/Destinations.kt`) are registered only when `BuildConfig.DEBUG`; the "Debug" row on the Settings page (`ui/settings/SettingsPage.kt`, shown only in debug builds) opens them. The shared `DebugPage` shows only the library's own things and has no slot for app-specific tools; the app has none yet. When it gets some, make an app debug page for them with a button that opens the library's page.
- The library comes from the Maven repo in `settings.gradle.kts`; change its version in `gradle/libs.versions.toml`.

<!-- ci-workflows:begin (managed by LukeNeedham/ci-workflows, do not edit) -->
- `.claude/skills/lukeneedham-conventions` and the callers `trigger_on_pull_request.yml`, `build_main.yml`, `delete_prereleases.yml`, `release.yml` in `.github/workflows/` are kept in sync from
  `LukeNeedham/ci-workflows` by CI, which keeps one PR titled "Sync from ci-workflows" open when they
  differ. Do not edit them here and do not copy them by hand: change them in ci-workflows. The callers
  hold no settings: every app is built the same way (see `docs/app-config.md` there).
- **Always give the user the AppHub link when work on this app completes.** As soon as a PR is open,
  paste `https://www.lukeneedham.com/apphub/pr/<repo name>/<PR number>` into the chat (CI posts the
  same link as a PR comment). Do not wait for the build and do not paste the APK link instead.
<!-- ci-workflows:end -->
