# Kiwami

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/kiwami_logo.png" width="128" alt="Kiwami logo">
</p>

<p align="center">
  <a href="https://github.com/crimznexus/Kiwami/releases/latest">
    <img src="https://img.shields.io/github/v/release/crimznexus/Kiwami?style=for-the-badge&logo=github&color=e0325b&label=Current%20Release" alt="Current Release">
  </a>
  <a href="https://github.com/crimznexus/Kiwami/releases">
    <img src="https://img.shields.io/github/downloads/crimznexus/Kiwami/total?style=for-the-badge&logo=github&color=2ea44f&label=Total%20Downloads" alt="Total Downloads">
  </a>
  <a href="https://github.com/crimznexus/Kiwami/stargazers">
    <img src="https://img.shields.io/github/stars/crimznexus/Kiwami?style=for-the-badge&logo=github&color=yellow&label=Stars" alt="Stars">
  </a>
  <a href="./LICENSE.md">
    <img src="https://img.shields.io/badge/License-UPL-blue?style=for-the-badge&logo=open-source-initiative&logoColor=white" alt="License: UPL">
  </a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-green?style=for-the-badge&logo=android" alt="Android 7.0+">
</p>

> **極み — The pinnacle of Anime & Manga on Android**

Kiwami is an AniList client that lets you stream and download anime through extensions and read manga, wrapped in a Liquid Glass UI. It is a fork of [ReDantotsu](https://github.com/AsrOfficialDev/ReDantotsu), which remade [Dantotsu](https://github.com/rebelonion/Dantotsu), which grew out of Saikou.

Kiwami (極み) means "the pinnacle" in Japanese.

## 📋 Table of Contents
- [Changes in Kiwami](#-changes-in-kiwami)
- [AI Page Enhancer](#-ai-page-enhancer)
- [Inherited from ReDantotsu](#-inherited-from-redantotsu)
- [Screenshots](#-screenshots)
- [Installation](#-installation)
- [Building from Source](#building-from-source)
- [Features](#-features)
- [Credits](#credits)
- [License](#license)
- [Disclaimer](#disclaimer)
- [Contributing](#-contributing)

## ✨ Changes in Kiwami

### Reading
- **Continuous chapters** — in the scrolling layouts, reaching the end of a chapter opens the next one by itself; no overscroll pull needed (the pull still works).
- **Next chapter predownloaded by default** — the auto downloader keeps one chapter ahead (adjustable per title in the download sheet, or off), and the reader opens downloaded chapters from storage instead of fetching them again.
- **AI page enhancer** — **Options (☰) → AI enhance** on a manga's chapter list upscales and cleans pages with an on-device Real-ESRGAN anime model (up to 2x, max 1600 px wide): all downloaded chapters at once, or chosen chapters, downloading any that aren't stored yet first. It runs in the background with a cancellable notification, resumes if interrupted, and replaces the downloaded pages; deleting the chapter removes them.
- **Rate-limit aware page loading** — sources that answer "too many requests" no longer show a raw JSON error: reading reports the wait, and downloads queue per source and retry after the requested delay. An empty page list can no longer wipe an already-downloaded chapter.

### Extensions
- **Current Keiyoushi / Mihon extensions work** — support for extensions built against extensions-lib 1.6 (now nearly all of Keiyoushi, e.g. Hiperdex and MangaDex): the suspend source API (`getSearchManga`, `getMangaUpdate`, …), `SManga`/`SChapter.memo`, OkHttp 5.3 with zstd, and kotlinx-serialization 1.9. An incompatible extension now shows a message instead of crashing the app.
- **Mihon-style repositories** — add a repository by its protobuf index URL (e.g. `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb`) as well as the older `index.min.json` / `repo.json` forms.
- **English first** — multi-language extensions start on their English source instead of the first one alphabetically (MangaDex used to open in Afrikaans).

### Interface
- **New identity** — a crimson hanko seal of 極 as the adaptive launcher icon, with a monochrome layer for Android 13+ themed icons and a matching TV banner.
- **Readable light theme** — the light Liquid Glass palette no longer draws white text and icons on light surfaces.
- **My Lists on the anime page** — custom AniList lists are one tap away from both the anime and manga home pages.

### Build
- **Own package and signing key** — Kiwami installs as `app.kiwami`, next to (not over) ReDantotsu; development builds use `app.kiwami.alpha`.
- **Smaller APK** — the 4.6 MB animated WebP splash is replaced by a 17 KB raster.
- **Buildable F-Droid flavor** — the `google-services`/Crashlytics plugins are applied only when a `google` variant is built, so the Firebase-free `fdroid` build no longer demands a `google-services.json`.
- **Android 7.0+** — `minSdk` is 24 so default interface methods survive in the APK; extensions call them at runtime.

## 🪄 AI Page Enhancer

Low-resolution or JPEG-mangled manhwa can be cleaned up on the phone itself, no server involved.

1. Open a manga's chapter list and tap the **Options** (☰) button next to the download button.
2. Tap **AI enhance**, then either:
   - **All downloaded (N)** to enhance every downloaded chapter, or
   - **Choose chapters** to pick any chapters (✓ marks downloaded ones). Chapters that aren't downloaded yet are downloaded first and enhanced as soon as each download finishes.
3. Read as usual once the notification says a chapter is done.

- **What it does:** runs the [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN) `animevideov3` model (bundled, ~2.5 MB) to upscale pages up to 2x (at most 1600 px wide) and remove blur and compression blocks.
- **Where the result goes:** enhanced pages **replace** the downloaded ones and stay until the chapter is deleted. Expect an enhanced chapter to take roughly 3–5x the storage.
- **Speed:** it times the phone's GPU/NPU (NNAPI) against its CPU on first use and keeps the faster one. Expect several seconds to a few tens of seconds per page, so a chapter takes minutes; it runs in the background with a cancellable notification and resumes if interrupted.
- **Works on stored pages**, so online reading is unaffected.

The model is converted from the official weights with [`tools/enhance/convert_realesr.py`](tools/enhance/convert_realesr.py); its BSD-3 license ships in `app/src/main/assets/enhance/`.

## 🌟 Inherited from ReDantotsu

These came from ReDantotsu rather than Kiwami:

- **Expanded Home Experience** — dedicated Anime and Manga sections.
- **Source Deduplication** — eliminates duplicate extension entries.
- **Liquid Glass UI** — real-time backdrop blur, pill-shaped bottom bars, sliding glass settings overlay, spring animations.
- **Enhanced Integration** — AniList login via modern dashboard redirect URIs, plus MyAnimeList rating support.

## 📸 Screenshots

> These are ReDantotsu's screenshots; the UI is unchanged in Kiwami apart from branding, so they still reflect the layout.

| Home | Manga | Anime |
|:---:|:---:|:---:|
| <img src="https://i.postimg.cc/Hn4Lk7DY/Home_page.jpg" width="300" /> | <img src="https://i.postimg.cc/Wz641JLk/Manga_page.jpg" width="300" /> | <img src="https://i.postimg.cc/KjrY8gSg/Anime_page.jpg" width="300" /> |

## 📥 Installation

1. Download an APK from the [latest release](https://github.com/crimznexus/Kiwami/releases/latest):
   - `Kiwami-arm64-v8a-release.apk` for almost every phone from the last several years,
   - `Kiwami-armeabi-v7a-release.apk` for older 32-bit phones,
   - `Kiwami-universal-release.apk` if unsure (larger; also runs on x86_64 emulators).

   Updates install over the previous version, since every release is signed with the same key.
2. Enable "Install from unknown sources" if prompted by your device.
3. Install, log in with AniList, and add an extension repository under **Settings → Extensions**.

## 🛠️ Building from Source <a name="building-from-source"></a>

**Requires JDK 21.** Gradle 8.11.1's bundled Kotlin compiler cannot parse a Java 25+ version string and fails with a bare `IllegalArgumentException: 25.0.2`, which names no file and gives no hint that the JDK is the problem.

```bash
git clone https://github.com/crimznexus/Kiwami.git
cd Kiwami

# Build the F-Droid flavor — no Firebase config needed
./gradlew assembleFdroidAlpha
```

The `google` flavor additionally needs an `app/google-services.json` (see `app/google-services.json.example`, whose `package_name` must match the `applicationId` in `app/build.gradle`):

```bash
./gradlew assembleGoogleAlpha
```

ABI splits are enabled, so `app/build/outputs/apk/` contains `armeabi-v7a` and `arm64-v8a` APKs plus a universal one. x86_64 emulators need the **universal** APK:

```bash
adb install -r -t app/build/outputs/apk/fdroid/alpha/Kiwami-universal-alpha.apk
```

Alpha builds install as `app.kiwami.alpha`, so they sit next to a release install.

### Release builds

Release builds are signed with a key kept outside the repository. Put its details in `~/.android-keys/kiwami-release.properties` (or point the `KIWAMI_SIGNING` environment variable at another file):

```properties
storeFile=/path/to/kiwami-release.jks
storePassword=...
keyAlias=kiwami
keyPassword=...
```

```bash
./gradlew assembleFdroidRelease
```

Without that file the release build falls back to the debug key, which Android treats as a different app.

### Tests

Instrumented tests (currently the page enhancer) run on a connected device or emulator:

```bash
./gradlew connectedFdroidAlphaAndroidTest
```

## 🎯 Features

- **AniList Sync** - Real-time synchronization with your AniList account.
- **MAL Sync** - Optional MyAnimeList integration for ratings.
- **Discord Rich Presence** - Show off what you're currently watching or reading to your friends.
- **Extension System** - Modular source system for unlimited content discovery.
- **Offline Mode** - Download content for offline viewing.
- **Auto-Skip** - Automatically skip openings, endings, and recaps.
- **Timestamp Support** - Community-powered timestamps.

## 🏛️ Credits <a name="credits"></a>

### Original Project
- **[Dantotsu](https://git.rebelonion.dev/rebelonion/Dantotsu)** by [rebelonion](https://github.com/rebelonion)
- Built from the ashes of Saikou

### ReDantotsu
- **Fan Remake Developer:** Ashraful ([AsrOfficialDev](https://github.com/AsrOfficialDev))
- **Liquid Glass Effect:** Based on iOS 26 design language
- **Backdrop Library:** [backdrop](https://github.com/kyant0/backdrop) by kyant0

### Kiwami
- **Fork Maintainer:** [crimznexus](https://github.com/crimznexus)

## 📜 License <a name="license"></a>

This project is licensed under the **Unabandon Public License (UPL)**, which extends GPLv3.

### Key Terms:
- ✅ **Free to use, modify, and distribute**
- ✅ **Source code must remain public** (GitHub fulfills this)
- ✅ **Same license for derivative works**
- ⚠️ **Must preserve original copyright notices**

> This is a derivative work of [Dantotsu](https://github.com/rebelonion/Dantotsu), licensed under GPLv3/UPL.

## ⚠️ Disclaimer <a name="disclaimer"></a>

- Kiwami does not host any content. All streaming sources come from 3rd party extensions.
- Kiwami is not affiliated with AniList, MyAnimeList, or any content providers.
- All anime/manga information is sourced from public APIs.
- The developers are not responsible for any misuse of the app.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
