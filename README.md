# Kiwami

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
  <img src="https://img.shields.io/badge/Android-6.0%2B-green?style=for-the-badge&logo=android" alt="Android 6.0+">
</p>

> **極み — The pinnacle of Anime & Manga on Android**

Kiwami is an AniList client that lets you stream and download anime through extensions and read manga, wrapped in a Liquid Glass UI. It is a fork of [ReDantotsu](https://github.com/AsrOfficialDev/ReDantotsu), which remade [Dantotsu](https://github.com/rebelonion/Dantotsu), which grew out of Saikou.

Kiwami (極み) means "the pinnacle" in Japanese.

## 📋 Table of Contents
- [Changes in Kiwami](#-changes-in-kiwami)
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

- **New identity** — new crimson "K + play" adaptive launcher icon, including a monochrome layer so Android 13+ themed icons work, and legacy raster icons so the launcher icon actually renders on Android 6–7 (upstream shipped no `ic_launcher.png` at all despite `minSdk 23`).
- **Smaller APK** — the 4.6 MB animated WebP splash (the repo's single largest file) is replaced by a 17 KB raster derived from the icon vector, and the orphaned launcher foreground PNGs are gone. The universal APK drops from 61.2 MB to 56.3 MB; arm64-v8a from 41.5 MB to 36.5 MB.
- **Buildable F-Droid flavor** — the `google-services`/Crashlytics plugins were being applied project-wide from inside the `google` product flavor, which forced the Firebase-free `fdroid` build to demand a `google-services.json`. They are now applied only when a `google` variant is actually built.

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

1. Download the latest APK from the [Releases](https://github.com/crimznexus/Kiwami/releases) page.
2. Enable "Install from unknown sources" if prompted by your device.
3. Install and enjoy!

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
adb install -r -t app/build/outputs/apk/fdroid/alpha/ReDantotsu-universal-alpha.apk
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
