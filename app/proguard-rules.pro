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

# Classes échangées avec le serveur : R8 retirait celles dont l'app ne lit
# jamais le contenu (AcceptedResponse). Retrofit lit le type de la réponse
# dans la signature de la méthode, n'y trouvait plus qu'Object et refusait
# l'appel avant tout envoi (« Mot de passe oublié » muet en 1.0.0-rc.1).
-keep,allowobfuscation class app.rengaine.data.remote.dto.** { *; }
-dontwarn okio.**

# ZXing : référence des classes de java.awt absentes d'Android, jamais
# utilisées sur ce chemin.
-dontwarn java.awt.**
