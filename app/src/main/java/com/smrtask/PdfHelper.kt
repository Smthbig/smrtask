package com.smrtask

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

object PdfHelper {

    // Constants for page size (A4)
    private const val PAGE_WIDTH = 595  // A4 width in points (8.3 inches at 72 points/inch)
    private const val PAGE_HEIGHT = 842 // A4 height in points (11.7 inches at 72 points/inch)
    private const val MARGIN = 40f
    private const val FONT_SIZE = 14f
    private const val LINE_SPACING = 1.4f
    private const val HEADER_TEXT = "Smrtask PDF Document"
    private const val HEADER_FONT_SIZE = 16f
    private const val FOOTER_FONT_SIZE = 12f

    // Function to create PDF
    fun exportToPdf(context: Context, htmlContent: String): String {
        return try {
            val document = PdfDocument()
            val paint = TextPaint().apply {
                textSize = FONT_SIZE
                isAntiAlias = true
                color = Color.BLACK
                typeface = Typeface.create("Segoe UI", Typeface.NORMAL)
            }

            // Convert HTML to plain text
            val plainText = cleanHtmlContent(htmlContent)
            val lines = plainText.split("\n")

            var pageCount = 1
            var currentPage = createNewPage(document, pageCount)
            var canvas = currentPage.canvas
            var y = drawHeader(canvas) + MARGIN

            for (line in lines) {
                val staticLayout = createStaticLayout(line, paint)

                // Check if text fits the current page
                if (y + staticLayout.height > PAGE_HEIGHT - MARGIN * 2) {
                    drawFooter(canvas, pageCount)
                    document.finishPage(currentPage)
                    pageCount++
                    currentPage = createNewPage(document, pageCount)
                    canvas = currentPage.canvas
                    y = drawHeader(canvas) + MARGIN
                }

                // Draw the text
                canvas.save()
                canvas.translate(MARGIN, y)
                staticLayout.draw(canvas)
                canvas.restore()
                y += staticLayout.height + LINE_SPACING * paint.textSize
            }

            // Draw footer on the last page
            drawFooter(canvas, pageCount)
            document.finishPage(currentPage)

            // Save the document
            savePdfDocument(context, document)
        } catch (e: Exception) {
            Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_LONG).show()
            ""
        }
    }

    // Function to clean HTML content to plain text
    private fun cleanHtmlContent(htmlContent: String): String {
        return try {
            Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("(?s)<style.*?</style>"), "") // Remove CSS
                .replace(Regex("<[^>]*>"), "")               // Remove HTML tags
                .trim()
        } catch (e: Exception) {
            "Failed to parse HTML content: ${e.message}"
        }
    }

    // Function to create a new page
    private fun createNewPage(document: PdfDocument, pageCount: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
        return document.startPage(pageInfo)
    }

    // Function to create StaticLayout (multi-line text)
    private fun createStaticLayout(text: String, paint: TextPaint): StaticLayout {
        return StaticLayout.Builder.obtain(text.trim(), 0, text.length, paint, (PAGE_WIDTH - MARGIN * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    // Function to draw header (Title)
    private fun drawHeader(canvas: Canvas): Float {
        val headerPaint = Paint().apply {
            textSize = HEADER_FONT_SIZE
            isAntiAlias = true
            color = Color.DKGRAY
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(HEADER_TEXT, PAGE_WIDTH / 2f, MARGIN, headerPaint)
        return MARGIN + HEADER_FONT_SIZE + 10f
    }

    // Function to draw footer (Page Number)
    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = Paint().apply {
            textSize = FOOTER_FONT_SIZE
            isAntiAlias = true
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - 20f, footerPaint)
    }

    // Function to save the PDF document
    private fun savePdfDocument(context: Context, document: PdfDocument): String {
        val fileName = "Smrtask_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.pdf"
        val fileDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Smrtask")
        if (!fileDir.exists()) fileDir.mkdirs()

        val file = File(fileDir, fileName)
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