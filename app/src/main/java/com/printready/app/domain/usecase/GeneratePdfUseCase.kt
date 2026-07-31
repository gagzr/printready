package com.printready.app.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.printready.app.domain.model.A4
import com.printready.app.domain.model.CanvasItem
import com.printready.app.domain.model.PrintJob
import java.io.File
import java.io.FileOutputStream

class GeneratePdfUseCase(private val context: Context) {

    fun execute(job: PrintJob, outputFile: File): Result<File> = runCatching {
        val document = PdfDocument()

        // Android's PdfDocument uses PostScript points (1/72 inch).
        // A4 210x297mm -> 595.275 pt x 841.889 pt
        val pageWidthPt = (210f / 25.4f * 72f).toInt() // approx 595
        val pageHeightPt = (297f / 25.4f * 72f).toInt() // approx 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val bgPaint = Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat(), bgPaint)

        for (item in job.items) {
            drawItem(canvas, item)
        }

        document.finishPage(page)

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
        outputFile
    }

    private fun mmToPt(mm: Float): Float {
        return mm / 25.4f * 72f
    }

    private fun drawItem(canvas: Canvas, item: CanvasItem) {
        val uri: Uri = item.imageUri ?: return
        val bitmap: Bitmap = loadBitmap(uri) ?: return

        // Compute size and position in PDF points
        val targetWidthPt = mmToPt(item.documentType.widthMm * item.scaleFactor)
        val targetHeightPt = mmToPt(item.documentType.heightMm * item.scaleFactor)
        val leftPt = mmToPt(item.offsetXMm)
        val topPt = mmToPt(item.offsetYMm)

        // Ensure dimensions are positive
        if (targetWidthPt <= 0 || targetHeightPt <= 0) {
            bitmap.recycle()
            return
        }

        // We want to map the original bitmap (width x height) to the target bounding box (targetWidthPt x targetHeightPt)
        // using the item's location and rotation.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val saved = canvas.save()
        // Compute center for rotation based on the final target size
        val pivotX = leftPt + targetWidthPt / 2f
        val pivotY = topPt + targetHeightPt / 2f
        canvas.rotate(item.rotationDeg, pivotX, pivotY)

        val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val dstRectF = android.graphics.RectF(leftPt, topPt, leftPt + targetWidthPt, topPt + targetHeightPt)

        // Draw the bitmap mapped exactly to the destination rectangle without creating an intermediate scaled bitmap
        canvas.drawBitmap(bitmap, srcRect, dstRectF, paint)

        canvas.restoreToCount(saved)
        bitmap.recycle()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
