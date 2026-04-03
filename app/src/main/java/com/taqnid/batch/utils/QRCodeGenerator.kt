package com.taqnid.batch.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Générateur de QR codes basé sur ZXing.
 * Utilisé pour :
 *   - les QR codes de lots (contenu = Taqnin ID)
 *   - les QR codes de manifestes de transfert
 */
object QRCodeGenerator {

    /**
     * Génère un Bitmap QR code pour le contenu fourni.
     *
     * @param content  Texte à encoder (ex : "TAQ-A3F2-2024" ou contenu manifeste)
     * @param sizePx   Taille en pixels du QR code (carré)
     * @param colorDark Couleur des modules sombres (ARGB)
     * @param colorLight Couleur de fond (ARGB)
     */
    fun generate(
        content: String,
        sizePx: Int = 512,
        colorDark: Int = android.graphics.Color.BLACK,
        colorLight: Int = android.graphics.Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            bitMatrixToBitmap(bitMatrix, colorDark, colorLight)
        } catch (e: WriterException) {
            null
        }
    }

    private fun bitMatrixToBitmap(
        matrix: BitMatrix,
        colorDark: Int,
        colorLight: Int
    ): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) colorDark else colorLight)
            }
        }
        return bmp
    }

    /**
     * Encode un Taqnin ID en QR code (utilisation lot individuel).
     */
    fun forTaqninId(taqninId: String, sizePx: Int = 512): Bitmap? =
        generate(taqninId, sizePx)

    /**
     * Encode un manifeste de transfert complet.
     */
    fun forTransfer(qrContent: String, sizePx: Int = 512): Bitmap? =
        generate(qrContent, sizePx)
}
