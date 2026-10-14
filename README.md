# PoetryBook

An Android book for your own poetry. Every page is a poem you wrote: swipe across the page to turn it, tap **Edit** to change it.

- **One poem per page**, with the title centred at the top
- **Page-turn swipe**: pages peel off with a page-curl effect (the geometry comes from [TurnThePage](https://github.com/LukeNeedham/TurnThePage); see `pageturn/`)
- **Edit** button on every page for the title and text; **Delete poem** while editing
- **Contents** drops down from the top like a reversed bottom sheet, listing every poem. Tap one to turn straight to it, or add a **New poem**
- Poems are saved automatically to a JSON file in the app's private storage (`poems.json`)

## Code layout

| Package | What |
| --- | --- |
| `pageturn/` | The page-turn UI. `PageTurnBook` shows composable pages one at a time and curls between them; `PageTurnState` drives drags and settle animations. `geometry/` and `turn/` are the framework-free curl maths copied from TurnThePage; `CurlPainter` draws the fold. No EPUB / reading code. |
| `ui/book/` | `BookScreen` (the app), `PoemPage` (read/edit one poem), `ContentsSheet` (the top drop-down), `PoemBookViewModel` |
| `data/` | `Poem` and `PoemStorage` |

## Stack

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
