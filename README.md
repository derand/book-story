> [!Caution]
>
> Starting in September 2026, Android will require all apps to be registered by verified developers
> in order to be installed on certified Android devices.
> It means that **Book's Story will no longer be available** for installation on any certified
> Android device.
>
> https://keepandroidopen.org/

---

<!------------ Header ------------>

<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon_2.png" width="120" />
  <h1>Book's Story</h1>
  <h3>Material You eBook Reader for Android</h3>
</div>

---

<!------------ Badges ------------>

<div align="center">

<a href="">[![License: GPL-3.0-only](https://img.shields.io/badge/GPL--3.0--only-9b3f57?label=License&labelColor=27303D)](LICENSE)</a>
<a href="">[![Build](https://github.com/derand/book-story/actions/workflows/build.yml/badge.svg?branch=develop)](https://github.com/derand/book-story/actions/workflows/build.yml)</a>

</div>

> [!Note]
>
> This is a fork of [Book's Story](https://github.com/Acclorite/book-story) by Acclorite, developed
> on its own. It is not affiliated with or endorsed by the original author — please report issues
> with this build here, not upstream.

<!------------ Overview ------------>

## Overview

<img src="fastlane/metadata/android/en-US/images/featureGraphic.png">

A customizable, open-source **reading app** built with Jetpack Compose — no ads, no trackers, and
no network access at all.

---

<!------------ Screenshots ------------>

## 🖌️ Screenshots

<div>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="30%" />
  <hr width=91%>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="30%" />
</div>

---

<!------------ Differences ------------>

## 🔀 What's different in this fork

- **Rebuilt FB2 and EPUB reading** — the reader text is built by walking the document tree, so
  formatting, footnotes, tables, image-only chapters and the author's own characters survive
  intact; EPUB documents are read as XML.
- **Fast repeat opens** — a parsed book is cached, and its images are kept with it.
- **Reading statistics** — sessions, time, words read, reading speed and coverage per book.
- **Backup and restore** — the library, progress and statistics go into one file and come back
  after a reinstall.
- **Page turning** — by tapping the screen edges or with the volume keys, over the continuous
  scroll.
- **Search** — within the open book, and in the library by author.
- **Open from a file manager** as a preview, and keep a book as a local copy.
- **Full-screen images** with zoom.
- **Hardened** — no file names or titles in the log, no device-to-device transfer of app data,
  covers decoded at the size they are drawn.

---

<!------------ Download guide ------------>

## 🚚 Downloads

> [!Important]
>
> The app supports only Android versions from **Android 8.0** and above.

<div>

[![Download Nightly](https://img.shields.io/badge/Download-Nightly%20APK-3f9b86?labelColor=27303D&logo=android&logoColor=white)](https://github.com/derand/book-story/releases/download/nightly/book-story-nightly.apk)

</div>

> Nightly is an automated **optimized** build (`release-debug`: minified and not
> debuggable) of the latest `develop` in this fork — `applicationId
> ...release.debug`, so it installs alongside a store build. Its provenance is
> verifiable — it really is built from this repo's code by CI:
> `gh attestation verify book-story-nightly.apk --repo derand/book-story`

---

<!------------ Credits and License ------------>

## 🤗 Credits

> [!Note]
>
> Also listed in About > Credits.

- [Tachiyomi (Mihon)](https://github.com/mihonapp/mihon) (GitHub)
- [Kitsune](https://github.com/Drumber/Kitsune) (GitHub)
- [Voyager](https://voyager.adriel.cafe/) (Voyager Website)
- [Material Design Icons](https://fonts.google.com/icons) (Google Fonts)
- [Material Design Fonts](https://fonts.google.com) (Google Fonts)
- [Weblate Translators](https://hosted.weblate.org/projects/book-story) (Hosted Weblate)
- [GitHub Badge](https://github.com/Kunzisoft/Github-badge) (GitHub)
- [Codeberg Badge](https://codeberg.org/Codeberg/GetItOnCodeberg) (Codeberg)

---

## 🧾 License

<div>
  <pre>

Book's Story — free and open-source Material You eBook reader.
Copyright © 2024-2026 Acclorite
Copyright © 2026 derand (modifications in this fork)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
  </pre>
</div>
