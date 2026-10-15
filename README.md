# PoetryBook

An Android book for your own poetry. Every page is a poem you wrote: swipe across the page to turn it, tap **Edit** to change it.

- **One poem per page**, with the title centred at the top of the page. The book always ends with exactly one blank, unstarted poem ("Untitled"): write in it and a fresh one appears after it
- **Turn pages** by swiping, or by tapping the left or right edge of the page
- **Page-turn swipe**: pages peel off with a page-curl effect (the geometry comes from [TurnThePage](https://github.com/LukeNeedham/TurnThePage); see `pageturn/`)
- A toolbar above the pages, with a ribbon button for **Contents**, and on the right the poem's **History**, its **Notepad**, a bin to **delete** the poem (with a confirmation) and a pencil to **edit** it (title and text). It is not part of the turning page; the pages turn below it
- **Contents** drops down from the top like a reversed bottom sheet, listing every poem. Tap one to turn straight to it. Its one button goes back to the **Bookshelf**
- **Bookshelf**: every book is a cover on the shelf. Create, rename, recolour and delete books from there
- **Stored in a Room database** (books, poems, poem versions), not in files
- **History**: tapping the tick after editing saves a new *version* of the poem, but only if the title or text changed. The history drop-down lists the versions (timestamp, title, first two lines); picking one opens it on a read-only page where the text can be selected and copied
- **Notepad**: a bottom sheet that is always a text field, for rhymes, metaphors and other ideas. It belongs to the poem as a whole, so every version shares it
- **Bin**: deleting a book or poem moves it to the bin (Settings → Bin) instead of wiping it. From the bin you can preview an item and restore it (a poem returns to its book and page) or delete it for good, which wipes its history too. There is no "empty bin"
- **Import** (Settings): a one-off copy of a folder of text files into the database. Every subfolder is a book and every `.txt` file in it a poem (loose files go into a book called Misc). The folder is not remembered or touched afterwards

## Code layout

| Package | What |
| --- | --- |
| `pageturn/` | The page-turn UI. `PageTurnBook` shows composable pages one at a time and curls between them; `PageTurnState` drives drags and settle animations. The pages next to the current one are kept composed and recorded so a turn can start instantly. `geometry/` and `turn/` are the framework-free curl maths copied from TurnThePage; `CurlPainter` draws the fold. No EPUB / reading code. |
| `ui/shelf/` | `ShelfScreen`, the bookshelf |
| `ui/book/` | `BookScreen` (one book), `PoemPage` (read/edit one poem), `BookToolbar`, `ContentsSheet` (the top drop-down), `HistoryMenu`, `NotepadSheet`, `PoemBookViewModel` |
| `ui/history/`, `ui/bin/`, `ui/settings/` | The read-only version page, the bin screens, and settings (bin entry and import) |
| `data/` | `Poem`, `PoemVersion`, `Book`; `PoemStore` (all database operations); `db/` (Room entities, DAOs, database); `DirectoryImporter` and `PoemFolder` (one-off import through the Storage Access Framework); `AppSettings` (shelf sort order, and what the old folder-based app remembered, used by import) |

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
- A poem with no title and no text is unstarted: it has no file, and loses it if you empty a poem out.
