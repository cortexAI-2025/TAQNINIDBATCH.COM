package com.taqnid.batch.ui.scanner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.taqnid.batch.R

/**
 * Vue personnalisée dessinant le cadre de visée du scanner.
 * Change de couleur selon le statut : scanning (blanc), succès (vert), erreur (rouge).
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Status { SCANNING, PROCESSING, SUCCESS, ERROR }

    private var currentStatus = Status.SCANNING

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val overlayPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
    }

    private val cornerLength = 60f
    private val cornerRadius = 12f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val frameSize = minOf(width, height) * 0.70f
        val left = (width - frameSize) / 2f
        val top = (height - frameSize) / 2f
        val right = left + frameSize
        val bottom = top + frameSize

        // Fond semi-transparent autour de la zone de scan
        canvas.drawRect(0f, 0f, width, top, overlayPaint)
        canvas.drawRect(0f, bottom, width, height, overlayPaint)
        canvas.drawRect(0f, top, left, bottom, overlayPaint)
        canvas.drawRect(right, top, width, bottom, overlayPaint)

        // Couleur des coins selon le statut
        cornerPaint.color = when (currentStatus) {
            Status.SCANNING, Status.PROCESSING -> Color.WHITE
            Status.SUCCESS -> Color.parseColor("#4CAF50")
            Status.ERROR -> Color.parseColor("#F44336")
        }

        // Dessiner les 4 coins
        // Haut-gauche
        canvas.drawLine(left, top + cornerLength, left, top + cornerRadius, cornerPaint)
        canvas.drawLine(left + cornerRadius, top, left + cornerLength, top, cornerPaint)
        // Haut-droit
        canvas.drawLine(right - cornerLength, top, right - cornerRadius, top, cornerPaint)
        canvas.drawLine(right, top + cornerRadius, right, top + cornerLength, cornerPaint)
        // Bas-gauche
        canvas.drawLine(left, bottom - cornerLength, left, bottom - cornerRadius, cornerPaint)
        canvas.drawLine(left + cornerRadius, bottom, left + cornerLength, bottom, cornerPaint)
        // Bas-droit
        canvas.drawLine(right - cornerLength, bottom, right - cornerRadius, bottom, cornerPaint)
        canvas.drawLine(right, bottom - cornerRadius, right, bottom - cornerLength, cornerPaint)
    }

    fun setStatus(status: Status) {
        currentStatus = status
        invalidate()
    }
}
