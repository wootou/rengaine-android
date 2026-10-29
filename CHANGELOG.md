# Changelog

All notable changes to the app. Versions follow [Semantic Versioning](https://semver.org/);
release candidates are published as GitHub pre-releases.

## [1.0.0-rc.3] - unreleased

### Added

- Crossfade between tracks (0 to 12 s), with the web player's rules: the fade ends when the sound stops,
  the next track starts where its sound begins, never more than a third of either track, and not
  between consecutive tracks of an album unless asked. Settings › Transitions, synced with the web player.
- Crossfade synced to the beat when both tracks have a beat grid and close tempos: the next track
  follows the tempo of the previous one (pitch kept), then returns to its own speed.
- "Preload the next track" setting, shared with the web player.
- Simplified audio on this device: no audio effects (volume can then only go down).
- Audio diagnostic log on this device, kept on the phone only, with copy and clear.

## [1.0.0-rc.2] - 2026-10-26

### Fixed

- Bottom bar buttons respond to the first tap on Samsung phones (they needed a quick double tap).
- "Forgot password" sends the request again: in 1.0.0-rc.1 the release build failed before contacting the server.

## [1.0.0-rc.1] - 2026-10-25

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
