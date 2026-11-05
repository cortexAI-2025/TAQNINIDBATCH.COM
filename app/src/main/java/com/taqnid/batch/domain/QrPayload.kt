package com.taqnid.batch.domain

import com.taqnid.batch.utils.TaqninIdGenerator
import java.net.URLDecoder

/**
 * Ce que contient un code scanné.
 *
 * Formats reconnus :
 *  - un identifiant de lot seul (provisoire TAQ-…, ancien format, ou officiel MA-…) ;
 *  - l'adresse de vérification publique imprimée sur les étiquettes web :
 *    https://…/verify?id=MA-… ;
 *  - le QR JSON de la plateforme web : {"id":"MA-…", …} ;
 *  - le QR d'un manifeste de transfert : TRF:{numéro}|FROM:…|TO:…|LOTS:… .
 */
sealed class QrPayload {
    data class BatchId(val taqninId: String, val official: Boolean) : QrPayload()
    data class TransferManifest(val transferNumber: String, val batchIds: List<String>) : QrPayload()
    data class Invalid(val reason: String) : QrPayload()

    companion object {
        private const val MAX_LENGTH = 2048
        private val JSON_ID = Regex("\"id\"\\s*:\\s*\"([^\"]{1,80})\"")
        private val TRANSFER_NUMBER = Regex("^TRF-\\d{4}-[0-9A-Z]{5,10}$")

        fun parse(raw: String?): QrPayload {
            val content = raw?.trim().orEmpty()
            if (content.isEmpty()) return Invalid("Code vide")
            if (content.length > MAX_LENGTH) return Invalid("Code trop long pour un code Taqnin")

            if (content.startsWith("TRF:")) return parseTransfer(content)

            val candidate = when {
                content.startsWith("{") -> JSON_ID.find(content)?.groupValues?.get(1)
                content.startsWith("http://", ignoreCase = true) ||
                    content.startsWith("https://", ignoreCase = true) -> idFromUrl(content)
                else -> content
            }?.trim()?.uppercase()
                ?: return Invalid("Ce code ne contient pas d'identifiant de lot")

            return if (TaqninIdGenerator.isValid(candidate)) {
                BatchId(candidate, official = TaqninIdGenerator.isWebId(candidate))
            } else {
                Invalid("« ${candidate.take(40)} » n'est pas un identifiant Taqnin")
            }
        }

        private fun idFromUrl(url: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            return query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == "id" }
                ?.let { runCatching { URLDecoder.decode(it[1], "UTF-8") }.getOrNull() }
        }

        private fun parseTransfer(content: String): QrPayload {
            val fields = content.split('|').associate { part ->
                val key = part.substringBefore(':')
                key to part.substringAfter(':', "")
            }
            val number = fields["TRF"].orEmpty()
            if (!TRANSFER_NUMBER.matches(number)) return Invalid("Numéro de transfert illisible")
            val lots = fields["LOTS"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (lots.any { !TaqninIdGenerator.isValid(it) }) return Invalid("Manifeste altéré : identifiant de lot invalide")
            return TransferManifest(number, lots)
        }
    }
}
