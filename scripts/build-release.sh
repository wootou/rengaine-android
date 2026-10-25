#!/usr/bin/env bash
# Construit l'APK de release signé, vérifie sa signature et calcule son
# empreinte SHA-256. Résultat dans dist/ : rengaine-<version>.apk et
# rengaine-<version>.apk.sha256. Voir RELEASING.md.
#
# La clé de signature n'est jamais dans le dépôt : chemin de son fichier de
# propriétés dans RENGAINE_KEYSTORE (par défaut ~/.rengaine/keystore.properties).
set -euo pipefail
cd "$(dirname "$0")/.."

# Empreinte du certificat de release (publique, aussi dans SECURITY.md).
EXPECTED="C4:DD:73:38:7F:C0:CD:E5:3F:1B:17:BE:5E:41:C5:3D:58:A2:6C:A5:55:50:5D:39:91:7C:99:91:93:4A:CF:E2"

props="${RENGAINE_KEYSTORE:-$HOME/.rengaine/keystore.properties}"
[ -f "$props" ] || { echo "Fichier de signature introuvable : $props" >&2; exit 1; }

if [ -n "$(git status --porcelain)" ]; then
    echo "Le dossier contient des modifications non commitées : commiter d'abord." >&2
    exit 1
fi

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
code=$(sed -n 's/^ *versionCode = \([0-9]*\)/\1/p' app/build.gradle.kts)
grep -q "^## \[$version\]" CHANGELOG.md || { echo "CHANGELOG.md n'a pas de section [$version]." >&2; exit 1; }

./gradlew --quiet clean testDebugUnitTest lintDebug assembleRelease -Prengaine.keystore="$props"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}}"
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner | sort -V | tail -1)
apk=app/build/outputs/apk/release/app-release.apk

actual=$("$apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
if [ "$actual" != "$EXPECTED" ]; then
    echo "Signature inattendue : $actual" >&2
    exit 1
fi

mkdir -p dist
out="dist/rengaine-$version.apk"
cp "$apk" "$out"
(cd dist && sha256sum "rengaine-$version.apk" > "rengaine-$version.apk.sha256")

echo "Version   $version (versionCode $code)"
echo "APK       $out"
echo "SHA-256   $(cut -d' ' -f1 "$out.sha256")"
echo "Signature $actual"
