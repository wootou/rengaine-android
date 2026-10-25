# Règles R8 de Rengaine.
#
# kotlinx.serialization embarque ses propres règles (META-INF/proguard) :
# les sérialiseurs générés sont gardés sans rien ajouter ici. Retrofit
# aussi. Ce qui reste à vérifier, c'est le comportement réel : chaque
# sérialiseur se teste sur un BUILD RELEASE installé (S3) — c'est en
# release que le piège Gson de Musique s'était caché.

# Retrofit garde les interfaces annotées ; les suspend ont besoin de
# Continuation avec ses génériques.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**

# ZXing : référence des classes de java.awt absentes d'Android, jamais
# utilisées sur ce chemin.
-dontwarn java.awt.**
