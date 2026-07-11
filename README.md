# Dual File Manager (Android, Kotlin)

A real, dual-pane Android file manager built on the **Storage Access Framework (SAF)** —
no `MANAGE_EXTERNAL_STORAGE` / broad storage permission needed. Each pane holds its own
independently-granted folder tree and navigates independently.

## Features
- Two independent panes, each opened via the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`)
- Tap a folder to open it, tap the active pane to make it the source for actions
- Tap a file to select it; long-press any item (file or folder) to select it without opening it
- **Copy** / **Move** selected items from the active pane into the other pane's current folder
- **Rename** (single item), **Delete** (multi-select, with confirmation), **New folder**
- **Search** — per-pane, recursive filename search from the pane's current folder down;
  results stream in live, and tapping one jumps the pane straight there (rebuilding its
  "Up" history correctly) and pre-selects the match
- Move uses the fast `DocumentsContract.moveDocument` path when both locations share the same
  document authority, and falls back to copy-then-delete otherwise
- All file I/O runs on a background coroutine (`Dispatchers.IO`) via `lifecycleScope`

## How to build
1. Open this folder (`DualFileManager/`) in **Android Studio** (Koala/Ladybug or newer).
2. Let Gradle sync — it will pull dependencies from Google/Maven Central (needs network).
3. Run on a device or emulator running **Android 7.0 (API 24)** or newer.

I generated this source but could not compile or run it myself — there's no Android SDK
or emulator in the environment I'm working in — so please build it in Android Studio and
let me know if anything doesn't compile; I'm happy to fix it.

## Using the app
1. Tap **Choose folder** in the left pane, then again in the right pane, and grant access
   to whatever two folders you want to manage (e.g. `Download` and a folder on an SD card).
2. Tap a pane's file list once to make it the **active** pane (top bar lights up green).
3. Select one or more items (tap a file, or long-press anything), then use the bottom
   toolbar: **Copy**/**Move** send selected items from the active pane into the other
   pane's currently open folder.

## Known limitations (v1)
- No search, sorting options, or thumbnails/previews yet
- No drag-and-drop between panes (uses the toolbar buttons instead)
- Folder picker must be re-chosen each cold app start unless you add code to persist and
  restore the last-used tree `Uri`s (permissions themselves *are* persisted via
  `takePersistableUriPermission`, so this is a quick addition if you want it)

## Project layout
```
DualFileManager/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/dualfilemanager/
        │   ├── MainActivity.kt
        │   ├── FileOps.kt
        │   ├── SearchEngine.kt
        │   └── adapter/
        │       ├── FileAdapter.kt
        │       └── SearchResultAdapter.kt
        └── res/
            ├── layout/ (activity_main.xml, pane_view.xml, item_file.xml,
            │            dialog_search_results.xml, item_search_result.xml)
            ├── values/ (strings.xml, colors.xml, themes.xml)
            └── drawable/ic_launcher.xml
```
