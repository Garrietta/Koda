<p align="center">
  <img src="docs/brand/rondo-mark.svg" width="120" alt="Rondo's mark: a scalloped record with a play triangle"/>
</p>

<h1 align="center">Rondo</h1>

<p align="center">
  <b>A music and video player for Android, with a DJ and your podcasts in it.</b>
</p>

Rondo plays music and video from YouTube Music, the files on your phone and the podcasts you follow, in one Material 3 Expressive app. It needs no API key and no account; signing in adds your playlists, likes and subscriptions.

A rondo is a piece of music whose theme keeps coming back. The app is built around the same idea: the things you return to are one tap from Home.

## Rondo and Koda

Rondo is a fork of [Koda](https://github.com/ivorisnoob/Koda) by ivorisnoob, and nearly everything it can do is Koda's work. It installs beside Koda as its own app (`com.garrietta.rondo`) and keeps its own data. To bring yours across, make a backup in Koda (Settings, Backup) and restore it in Rondo.

What is different here:

- **Its own identity.** A new name and mark, icon presets redrawn to match, and one bundled typeface, Google Sans Flex, in place of fonts fetched at first launch.
- **Home is blocks.** A few large flat tiles, each a place to go: the DJ, **On repeat** (the songs you played three times or more in the last four weeks), Liked, **New episodes** from the shows you follow, and what you played last. Explore, Playlists, Library, Charts, Quick picks and New are one tap below. A tile opens as a page of its own that grows out of the tile and keeps its color. Color tells the tiles apart, so artwork is an accent rather than the layout.
- **Its own colors.** Iris, orange, orchid and cream as flat blocks on a near-black ground, written out role by role instead of grown from a seed. The app keeps them while music plays; the player takes the album's.
- **AI DJ.** A DJ that builds a rolling queue from what you listen to and talks between songs, one tap from the navigation bar.
- **Podcasts.** Follow any show by its RSS feed; new episodes arrive on their own.
- **Fixes carried here**, including the one that brought liked songs back.

The full tour of the player, video, downloads, widgets and settings is in [Koda's README](docs/koda-readme.md), kept as it was written.

## Build

```sh
./gradlew compileDebugKotlin    # fastest check
./gradlew assembleDebug         # debug APK, installs as com.garrietta.rondo.debug
```

JDK 17 or newer and Android SDK platform 37. Dependencies come from Google, Maven Central and JitPack.

## Changing the name or the mark

Both are generated, so neither is edited by hand:

- `python3 tools/rename_app.py Rondo <NewName>` renames the app everywhere a person reads it, in every language. Class names, package names and links to Koda are left alone on purpose, which keeps merges from Koda small.
- `python3 tools/rondo_brand.py` writes the launcher icon, its presets, the themed icon and the splash animation from one description of the mark. `--preview` also writes the pictures in `docs/brand/`.

## License and credit

GPL-3.0, the same as Koda. Rondo's source is this repository. Koda is copyright its authors; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the libraries and the typeface.
