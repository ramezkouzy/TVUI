# Shasha

A clean, minimal IPTV player for Fire TV and Google TV, built for Xtream Codes providers, with English subtitles as a first-class feature.

## Install on Fire TV

1. Open **Downloader**.
2. Enter: `https://github.com/ramezkouzy/TVUI/releases/latest/download/Shasha.apk`
3. Install, then open **Shasha** from your apps.

Updates install the same way, over the old version (your settings are kept).

## First run

- Sign in with your provider's **server URL, username and password**.
- For English subtitles: make a free account at opensubtitles.com, create an **API consumer** in your profile, and paste the key in **Settings**.

## Remote

| Button | Movies & series | Live TV |
|---|---|---|
| OK / Play | Pause / play | Pause / play |
| ◀ ▶ | Skip 10s back / forward | — |
| ▼ or Menu | Subtitle menu | Next channel |
| ▲ | Show progress | Previous channel |

## How it's built

Kotlin + Jetpack Compose for TV, Media3 ExoPlayer, OkHttp. Every push to `main` builds a signed APK with GitHub Actions and publishes it as the latest release.
