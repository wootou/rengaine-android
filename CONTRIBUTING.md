# Contributing

## Build

Requirements: JDK 17 and the Android SDK (platform 36). Gradle is provided by the wrapper. Step by step, including signing with your own key: [BUILDING.md](BUILDING.md).

```sh
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

The debug build installs next to the release one (`org.rengaine.app.debug`). To try it you need a [Rengaine](https://github.com/wootou/rengaine) server; pair the app with a QR code from the web player (Settings › Connected devices).

Release builds are signed with a key that is never stored in this repository.

## Guidelines

- The app follows the server's API contract (`/api/v2`); it never talks to rengaine.org.
- User-facing text lives in `app/src/main/res/values/strings.xml` (English) and `values-fr/` (French); please update both.
- Never commit tokens, passwords or personal server addresses.

---

# Contribuer

Prérequis : JDK 17 et le SDK Android (plateforme 36) ; pas à pas dans [BUILDING.md](BUILDING.md). Compiler avec `./gradlew assembleDebug`, tester avec `./gradlew testDebugUnitTest`. Il faut un serveur [Rengaine](https://github.com/wootou/rengaine) pour l'essayer : appairer l'app par QR code depuis le lecteur web (Réglages › Appareils connectés). Les textes sont dans `values/strings.xml` (anglais) et `values-fr/` (français) : mettre à jour les deux. Jamais de jeton, de mot de passe ni d'adresse de serveur personnel dans le dépôt.
