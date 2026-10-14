# PoetryBook

An Android book for your own poetry. Every page is a poem you wrote: swipe across the page to turn it, tap **Edit** to change it.

- **One poem per page**, with the title centred at the top of the page. The last page is always a blank **New poem** page: starting a poem there turns it into that poem and adds a fresh one after it
- **Turn pages** by swiping, or by tapping the left or right edge of the page
- **Page-turn swipe**: pages peel off with a page-curl effect (the geometry comes from [TurnThePage](https://github.com/LukeNeedham/TurnThePage); see `pageturn/`)
- A toolbar with a ribbon button for **Contents** and a pencil to **edit** the poem (title and text); **Delete poem** while editing
- **Contents** drops down from the top like a reversed bottom sheet, listing every poem. Tap one to turn straight to it
- **Bookshelf**: the folder you choose is a library. Each subfolder is a **book**, shown as a cover on the shelf; text files directly in the folder are gathered into a book called **Misc**. You can create new books from the shelf, and get back to it from Contents
- **Each poem is its own `.txt` file**, named after the poem's title, inside its book's folder, outside the app, so your poems survive reinstalling it
- **Onboarding** has a step to pick that folder and grant PoetryBook access to it (Android's folder picker, with a persisted permission)
- **Settings** (from the shelf or the Contents sheet) shows the folder, can open it in the system file browser, and can switch to a different folder

## Code layout

| Package | What |
| --- | --- |
| `pageturn/` | The page-turn UI. `PageTurnBook` shows composable pages one at a time and curls between them; `PageTurnState` drives drags and settle animations. `geometry/` and `turn/` are the framework-free curl maths copied from TurnThePage; `CurlPainter` draws the fold. No EPUB / reading code. |
| `ui/shelf/` | `ShelfScreen`, the bookshelf |
| `ui/book/` | `BookScreen` (one book), `PoemPage` (read/edit one poem), `NewPoemPage`, `ContentsSheet` (the top drop-down), `PoemBookViewModel` (the library and the open book) |
| `ui/onboarding/`, `ui/settings/` | First-run folder setup and the settings page |
| `data/` | `Poem`; `Library` (the picked folder as books), `PoemFolder` (a directory in it, via the Storage Access Framework); `PoemRepository` (poems ⇄ files, renaming files as titles change); `AppSettings` (chosen folder and each book's page order) |

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

- In the chosen folder, every subfolder (except hidden ones starting with `.`) is a book, and `.txt` files directly in the folder form the Misc book. Misc is always on the shelf.
- A poem's file name is its title (characters file systems reject are dropped; a blank title is `Untitled`; clashes become `Title (2).txt`). The file's contents are the poem text.
- Editing a title renames the file; deleting a poem deletes its file. Changes are written shortly after you stop typing and when the app goes to the background.
- Each book's page order is kept in the app's settings, since text files can't hold it. After a reinstall, poems come back in alphabetical order.
- Poems are read from the folder when the app starts; edits made to the files while the app is open are picked up on the next start.
- Choosing a different folder loads the books in that folder; your current books and poems stay in the old folder.
- A new poem has no file until something is written in it.
