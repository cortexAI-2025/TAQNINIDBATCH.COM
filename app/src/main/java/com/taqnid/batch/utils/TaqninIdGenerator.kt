package com.taqnid.batch.utils

import java.security.SecureRandom
import java.util.Calendar

/**
 * Identifiants de lot.
 *
 * Deux familles coexistent :
 *  - l'identifiant officiel de la plateforme web V2.5, référence réglementaire :
 *    MA-{RÉGION}-{COOPÉRATIVE}-{VARIÉTÉ}-{ANNÉE}-{NNNN}, ex. MA-RIF-AKN01-BELD-2026-0001.
 *    Son numéro d'ordre est attribué par le serveur web : l'application ne le
 *    fabrique jamais elle-même, faute de pouvoir le garantir unique hors ligne ;
 *  - l'identifiant provisoire créé sur le terrain, hors ligne :
 *    TAQ-{ANNÉE}-{10 caractères}, ex. TAQ-2026-7K3M9QX2PD. Dix caractères en
 *    base 32 de Crockford (sans I, L, O, U, ambigus à la lecture) tirés au
 *    hasard cryptographique, soit 50 bits : le risque que deux appareils
 *    tirent le même identifiant reste négligeable même sur des millions de
 *    lots. L'ancien format TAQ-XXXX-AAAA (20 bits seulement : une collision
 *    devenait probable vers 1 300 lots par an) reste lu, plus produit.
 */
object TaqninIdGenerator {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    private val PROVISIONAL = Regex("^TAQ-(\\d{4})-[0-9A-HJKMNP-TV-Z]{10}$")
    private val LEGACY = Regex("^TAQ-[A-Z0-9]{4}-(\\d{4})$")
    private val WEB = Regex("^MA-[A-Z0-9]{2,12}-[A-Z0-9]{1,20}-[A-Z0-9]{1,12}-(\\d{4})-\\d{4}$")

    /** Un nouvel identifiant provisoire pour l'année donnée (l'année courante par défaut). */
    fun generate(year: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        val suffix = buildString { repeat(10) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        return "TAQ-$year-$suffix"
    }

    /** Un identifiant de lot lisible par l'application, quelle que soit sa famille. */
    fun isValid(taqninId: String): Boolean =
        isProvisional(taqninId) || isLegacy(taqninId) || isWebId(taqninId)

    fun isProvisional(taqninId: String) = PROVISIONAL.matches(taqninId)

    fun isLegacy(taqninId: String) = LEGACY.matches(taqninId)

    /** L'identifiant officiel attribué par la plateforme web. */
    fun isWebId(taqninId: String) = WEB.matches(taqninId)

    fun extractYear(taqninId: String): Int? =
        (PROVISIONAL.find(taqninId) ?: LEGACY.find(taqninId) ?: WEB.find(taqninId))
            ?.groupValues?.get(1)?.toIntOrNull()

    /** Numéro de transfert : TRF-{ANNÉE}-{8 caractères}, unique sans coordination. */
    fun generateTransferNumber(year: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        val suffix = buildString { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        return "TRF-$year-$suffix"
    }
}
