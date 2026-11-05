package com.taqnid.batch.domain

/**
 * Que faire d'un lot modifié hors ligne au moment de le synchroniser.
 *
 * Chaque lot porte la version serveur sur laquelle reposent les saisies
 * locales (baseVersion). Le serveur n'accepte une mise à jour que si elle
 * part de sa version courante : deux appareils qui ont modifié le même lot
 * sans se voir ne s'écrasent donc jamais en silence.
 */
sealed class SyncDecision {
    /** Le lot n'existe pas encore sur le serveur : le créer en version 1. */
    object Create : SyncDecision()

    /** Le serveur est resté à la version de départ : écrire la version suivante. */
    data class Update(val nextVersion: Long) : SyncDecision()

    /** Le serveur contient déjà exactement cet état (envoi précédent dont la réponse s'est perdue). */
    data class AlreadyApplied(val serverVersion: Long) : SyncDecision()

    /** Un autre appareil a modifié le lot entre-temps : ne rien écraser, signaler. */
    data class Conflict(val serverVersion: Long, val message: String) : SyncDecision()

    companion object {
        /**
         * @param baseVersion version serveur connue de l'appareil (0 si le lot est né sur l'appareil)
         * @param serverVersion version actuelle sur le serveur (null si absent)
         * @param sameContent l'état serveur est identique à l'état local
         */
        fun decide(baseVersion: Long, serverVersion: Long?, sameContent: Boolean): SyncDecision = when {
            serverVersion == null -> Create
            sameContent -> AlreadyApplied(serverVersion)
            serverVersion == baseVersion -> Update(baseVersion + 1)
            else -> Conflict(
                serverVersion,
                "Ce lot a été modifié sur un autre appareil (version $serverVersion, la vôtre partait de la " +
                    "version $baseVersion). Choisissez la version à garder."
            )
        }
    }
}
