package com.taqnid.batch.utils

import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

/**
 * Générateur d'identifiants Taqnin ID uniques.
 * Format : TAQ-XXXX-YYYY
 *   XXXX = 4 caractères alphanumériques aléatoires
 *   YYYY = année courante
 *
 * Exemple : TAQ-A3F2-2024
 */
object TaqninIdGenerator {

    private val chars = ('A'..'Z') + ('0'..'9')
    private val counter = AtomicInteger(0)

    /**
     * Génère un nouvel identifiant Taqnin unique.
     */
    fun generate(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val random = buildString {
            repeat(4) { append(chars.random()) }
        }
        return "TAQ-$random-$year"
    }

    /**
     * Valide qu'une chaîne respecte le format Taqnin ID.
     */
    fun isValid(taqninId: String): Boolean {
        return taqninId.matches(Regex("^TAQ-[A-Z0-9]{4}-\\d{4}$"))
    }

    /**
     * Extrait l'année depuis un Taqnin ID.
     */
    fun extractYear(taqninId: String): Int? {
        return if (isValid(taqninId)) taqninId.takeLast(4).toIntOrNull() else null
    }
}
