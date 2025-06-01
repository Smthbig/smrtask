package com.smrtask

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfHelper {

    //  A4 size in points (1 point = 1/72 inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val FONT_SIZE = 14f
    private const val LINE_SPACING = 1.4f
    private const val HEADER_FONT_SIZE = 16f
    private const val FOOTER_FONT_SIZE = 12f
    private const val HEADER_TEXT = "Smrtask PDF Document"

    /**
     * Exports full HTML content to a PDF with basic formatting.
     * Handles pagination and plain-to-styled conversion internally.
     */
    fun exportToPdf(context: Context, htmlContent: String): String {
        return try {
            val document = PdfDocument()

            val paint = TextPaint().apply {
                textSize = FONT_SIZE
                isAntiAlias = true
                color = Color.BLACK
                typeface = Typeface.SANS_SERIF
            }

            // Remove <style> tags to prevent raw CSS printing
            val cleanedHtml = htmlContent.replace(
                Regex("<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL),
                ""
            )

            // Use cleaned HTML content
            val spanned: Spanned = Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_LEGACY)

            val contentWidth = (PAGE_WIDTH - MARGIN * 2).toInt()

            val layout = StaticLayout.Builder
                .obtain(spanned, 0, spanned.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, LINE_SPACING)
                .setIncludePad(false)
                .build()

            var pageCount = 1
            var startLine = 0
            val totalLines = layout.lineCount

            while (startLine < totalLines) {
                val page = createNewPage(document, pageCount)
                val canvas = page.canvas

                val yStart = drawHeader(canvas) + MARGIN

                canvas.save()
                canvas.translate(MARGIN, yStart)

                val pageHeightAvailable = (PAGE_HEIGHT - (yStart + MARGIN + 40)).toInt()

                val pageLayout = createPagedLayout(layout, paint, startLine, pageHeightAvailable)
                pageLayout.draw(canvas)

                canvas.restore()

                drawFooter(canvas, pageCount)
                document.finishPage(page)

                startLine += pageLayout.lineCount
                pageCount++
            }

            savePdfDocument(context, document)
        } catch (e: Exception) {
            Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_LONG).show()
            ""
        }
    }

    /**
     * Creates a new A4 page in the document
     */
    private fun createNewPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(pageInfo)
    }

    /**
     * Splits content layout into paged chunks starting from [startLine]
     */
    private fun createPagedLayout(
        originalLayout: StaticLayout,
        paint: TextPaint,
        startLine: Int,
        availableHeight: Int
    ): StaticLayout {
        val text = originalLayout.text
        var endLine = startLine
        var height = 0

        while (endLine < originalLayout.lineCount) {
            val top = originalLayout.getLineTop(startLine)
            val bottom = originalLayout.getLineBottom(endLine)
            val currentHeight = bottom - top

            if (currentHeight > availableHeight) break

            height = currentHeight
            endLine++
        }

        val startOffset = originalLayout.getLineStart(startLine)
        val endOffset = originalLayout.getLineEnd(endLine.coerceAtMost(originalLayout.lineCount - 1))
        val contentWidth = (PAGE_WIDTH - MARGIN * 2).toInt()

        return StaticLayout.Builder
            .obtain(text, startOffset, endOffset, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    /**
     * Draws a top header and returns the height used
     */
    private fun drawHeader(canvas: Canvas): Float {
        val paint = Paint().apply {
            textSize = HEADER_FONT_SIZE
            color = Color.DKGRAY
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(HEADER_TEXT, PAGE_WIDTH / 2f, MARGIN, paint)
        return MARGIN + HEADER_FONT_SIZE + 10f
    }

    /**
     * Draws a page footer
     */
    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val paint = Paint().apply {
            textSize = FOOTER_FONT_SIZE
            color = Color.GRAY
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - 20f, paint)
    }

    /**
     * Saves the generated PDF to Documents/Smrtask/
     */
    private fun savePdfDocument(context: Context, document: PdfDocument): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Smrtask_$timestamp.pdf"
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val smrtaskDir = File(documentsDir, "Smrtask").apply {
            if (!exists()) mkdirs()
        }

        val file = File(smrtaskDir, fileName)
        return try {
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            Toast.makeText(context, "PDF saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            file.absolutePath
        } catch (e: Exception) {
            document.close()
            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
            ""
        }
    }
}