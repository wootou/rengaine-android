# Building Rengaine from source

**English** | [Français](#compiler-rengaine-soi-même)

You can build the app yourself instead of installing the APK from the
[releases](https://github.com/wootou/rengaine-android/releases).

## Requirements

- **JDK 17** (for example Eclipse Temurin 17 or OpenJDK 17).
- **Android SDK** with platform 36 and build tools: install
  [Android Studio](https://developer.android.com/studio), or only the
  [command-line tools](https://developer.android.com/studio#command-line-tools-only) and then
  `sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"`.
- **Git**. Gradle itself is downloaded by the wrapper (`./gradlew`), its checksum is verified.

Tell Gradle where the SDK is, with either the `ANDROID_HOME` environment variable or a
`local.properties` file at the root of the project (never committed):

```properties
sdk.dir=/path/to/Android/Sdk
```

## Get the source

```sh
git clone https://github.com/wootou/rengaine-android.git
cd rengaine-android
git checkout v1.0.0-rc.1   # optional: the code of a given release
```

## Debug build (simplest)

```sh
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install` or copy it to
the phone. It is called **Rengaine (debug)** and installs **next to** the official app
(package `org.rengaine.app.debug`), so both can be tried side by side.

## Release build signed with your own key

1. Create a key once (keep the file and passwords safe, outside the project):

   ```sh
   keytool -genkeypair -v -keystore ~/rengaine-own.jks -alias rengaine \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Write a properties file next to it, for example `~/rengaine-own.properties`:

   ```properties
   storeFile=/home/you/rengaine-own.jks
   storePassword=…
   keyAlias=rengaine
   keyPassword=…
   ```

3. Build:

   ```sh
   ./gradlew assembleRelease -Prengaine.keystore=$HOME/rengaine-own.properties
   ```

   The APK is `app/build/outputs/apk/release/app-release.apk` (minified with R8).

Without `-Prengaine.keystore` (and without `~/.rengaine/keystore.properties`), the release build is
signed with the debug key: fine for a quick test, not for daily use.

An APK signed with your own key **cannot update** the official one, and the other way round:
Android requires the same signature. Uninstall the other one first; your favourites, playlists and
history stay on your server, you only need to sign in again.

## Tests and checks

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
```

The same checks run on every push (GitHub Actions, `.github/workflows/ci.yml`).

## Check an official APK

Each release ships the APK and its `.sha256` file:

```sh
sha256sum -c rengaine-1.0.0-rc.1.apk.sha256
apksigner verify --print-certs rengaine-1.0.0-rc.1.apk
```

The certificate SHA-256 must match the fingerprint given in [SECURITY.md](SECURITY.md). The build is
not bit-for-bit reproducible yet: an APK you build will not have the same checksum as the official
one.

---

# Compiler Rengaine soi-même

**Prérequis :** JDK 17, SDK Android (plateforme 36, build tools ; par Android Studio ou les outils en
ligne de commande), Git. Indiquer le SDK par `ANDROID_HOME` ou par `sdk.dir=…` dans
`local.properties`.

**Récupérer le code :** `git clone https://github.com/wootou/rengaine-android.git`, puis
éventuellement `git checkout v1.0.0-rc.1` pour le code d'une version.

**Version debug (le plus simple) :** `./gradlew assembleDebug` ; l'APK
`app/build/outputs/apk/debug/app-debug.apk` s'installe **à côté** de l'app officielle
(« Rengaine (debug) »).

**Version release signée avec votre clé :** créer une clé avec `keytool` (commande ci-dessus), écrire
le fichier de propriétés (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), puis
`./gradlew assembleRelease -Prengaine.keystore=/chemin/du/fichier.properties`. Sans clé, la release
est signée avec la clé debug (pour un essai seulement). Un APK signé avec votre clé ne peut pas mettre
à jour l'app officielle, et inversement : désinstaller l'autre d'abord (favoris, playlists et
historique restent sur le serveur, il suffit de se reconnecter).

**Vérifications :** `./gradlew testDebugUnitTest lintDebug`. **Vérifier un APK officiel :**
`sha256sum -c` avec le fichier `.sha256` de la release, et `apksigner verify --print-certs` :
l'empreinte doit être celle de [SECURITY.md](SECURITY.md).
