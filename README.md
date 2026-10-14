# PoetryBook

An Android book for your own poetry. Every page is a poem you wrote: swipe across the page to turn it, tap **Edit** to change it.

- **One poem per page**, with the title centred at the top
- **Page-turn swipe**: pages peel off with a page-curl effect (the geometry comes from [TurnThePage](https://github.com/LukeNeedham/TurnThePage); see `pageturn/`)
- **Edit** button on every page for the title and text; **Delete poem** while editing
- **Contents** drops down from the top like a reversed bottom sheet, listing every poem. Tap one to turn straight to it, or add a **New poem**
- **Each poem is its own `.txt` file**, named after the poem's title, in a folder you choose, outside the app, so your poems survive reinstalling it
- **Onboarding** has a step to pick that folder and grant PoetryBook access to it (Android's folder picker, with a persisted permission)
- **Settings** (from the Contents sheet) shows the folder, can open it in the system file browser, and can switch to a different folder

## Code layout

| Package | What |
| --- | --- |
| `pageturn/` | The page-turn UI. `PageTurnBook` shows composable pages one at a time and curls between them; `PageTurnState` drives drags and settle animations. `geometry/` and `turn/` are the framework-free curl maths copied from TurnThePage; `CurlPainter` draws the fold. No EPUB / reading code. |
| `ui/book/` | `BookScreen` (the app), `PoemPage` (read/edit one poem), `ContentsSheet` (the top drop-down), `PoemBookViewModel` |
| `ui/onboarding/`, `ui/settings/` | First-run folder setup and the settings page |
| `data/` | `Poem`; `PoemFolder` (the picked folder, via the Storage Access Framework); `PoemRepository` (poems ⇄ files, renaming files as titles change); `AppSettings` (chosen folder and page order) |

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

## How poems are stored

- A poem's file name is its title (characters file systems reject are dropped; a blank title is `Untitled`; clashes become `Title (2).txt`). The file's contents are the poem text.
- Editing a title renames the file; deleting a poem deletes its file. Changes are written shortly after you stop typing and when the app goes to the background.
- Page order is kept in the app's settings, since text files can't hold it. After a reinstall, poems come back in alphabetical order.
- Poems are read from the folder when the app starts; edits made to the files while the app is open are picked up on the next start.
- Choosing a different folder shows the poems already in it, or, if it has none, copies your current poems into it. Files in the old folder are left alone.
