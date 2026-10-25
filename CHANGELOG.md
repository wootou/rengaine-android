# Changelog

All notable changes to the app. Versions follow [Semantic Versioning](https://semver.org/);
release candidates are published as GitHub pre-releases.

## [1.0.0-rc.1] - unreleased

First public release candidate. Built against the Rengaine server API contract 2.27.0.

### Features

- Sign in with a server address, email and password, or pair with a QR code from the web player.
- Albums, artists, explore (genres, decades, collections, moods), random, playlists and favourites.
- Search across tracks, albums and artists.
- Player with queue (reorder, remove), repeat, infinite mode, waveform and volume normalisation.
- Background playback with notification and lock-screen controls, including a favourite button.
- Stream quality per network (original or converted) for wifi and mobile data, with a playback cache.
- Listening history, synced with the web player.
- Settings shared with the web player: theme, infinite mode, continuous playback, connected devices,
  data export and account deletion.
- English and French.
- Open source licences screen (Settings › About).

### Security

- `http://` servers are accepted but flagged with a warning; HTTPS is recommended.
