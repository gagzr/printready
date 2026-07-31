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

        // High-resolution A4 page at 300 DPI (2480 x 3508 px)
        val pageWidthPx = A4.WIDTH_PX
        val pageHeightPx = A4.HEIGHT_PX

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPx, pageHeightPx, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val bgPaint = Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidthPx.toFloat(), pageHeightPx.toFloat(), bgPaint)

        for (item in job.items) {
            drawItem(canvas, item)
        }

        document.finishPage(page)

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
        outputFile
    }

    private fun mmToPx(mm: Float): Float {
        return A4.mmToPx(mm)
    }

    private fun drawItem(canvas: Canvas, item: CanvasItem) {
        val uri: Uri = item.imageUri ?: return
        val bitmap: Bitmap = loadBitmap(uri) ?: return

        // Compute size and position in 300 DPI canvas pixels
        val effectiveWidthMm = item.overrideWidthMm ?: (item.documentType.widthMm * item.scaleFactor)
        val initialHeightMm = item.documentType.widthMm / (item.overrideAspectRatio ?: (item.documentType.widthMm / item.documentType.heightMm.coerceAtLeast(1f)))
        val effectiveHeightMm = item.overrideHeightMm ?: (initialHeightMm * item.scaleFactor)
        
        val targetWidthPx = mmToPx(effectiveWidthMm)
        val targetHeightPx = mmToPx(effectiveHeightMm)
        val leftPx = mmToPx(item.offsetXMm)
        val topPx = mmToPx(item.offsetYMm)

        // Ensure dimensions are positive
        if (targetWidthPx <= 0 || targetHeightPx <= 0) {
            bitmap.recycle()
            return
        }

        // High quality bitmap scaling paint
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        val saved = canvas.save()
        // Compute center for rotation based on the final target size
        val pivotX = leftPx + targetWidthPx / 2f
        val pivotY = topPx + targetHeightPx / 2f
        canvas.rotate(item.rotationDeg, pivotX, pivotY)

        val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val dstRectF = android.graphics.RectF(leftPx, topPx, leftPx + targetWidthPx, topPx + targetHeightPx)

        // Draw original high-res bitmap mapped directly to destination rectangle
        canvas.drawBitmap(bitmap, srcRect, dstRectF, paint)

        canvas.restoreToCount(saved)
        bitmap.recycle()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
