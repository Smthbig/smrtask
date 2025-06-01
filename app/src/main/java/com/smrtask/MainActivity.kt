package com.smrtask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etQuestion: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSavePdf: MaterialButton
    private lateinit var webViewResponse: WebView
    private lateinit var cardWebView: MaterialCardView
    private lateinit var progressLoading: ProgressBar
    private lateinit var geminiHelper: GeminiHelper

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupWebView()
        setupGeminiHelper()

        viewModel.responseHtml.value?.let { displayResponse(it) }
        viewModel.lastInput.value?.let { etQuestion.setText(it) }

        if (viewModel.chatHistory.isEmpty()) displayWelcomeMessage()
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupViews() {
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        btnSavePdf = findViewById(R.id.btnSavePdf)
        webViewResponse = findViewById(R.id.webViewResponse)
        cardWebView = findViewById(R.id.cardWebView)
        progressLoading = findViewById(R.id.progressLoading)

        btnSend.setOnClickListener { onSendClicked() }
        btnSettings.setOnClickListener { openSettings() }
        btnSavePdf.setOnClickListener { exportChatToPdf() }

        etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }
    }

    private fun setupWebView() {
        webViewResponse.apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            }
        }
    }

    private fun setupGeminiHelper() {
        geminiHelper = GeminiHelper(
            context = this,
            errorHandler = { showError(it) },
            uiCallback = { block -> runOnUiThread(block) }
        )
    }

    private fun onSendClicked() {
        val question = etQuestion.text?.toString()?.trim().orEmpty()
        if (question.isBlank()) {
            showToast("Please enter a question.")
            return
        }
        viewModel.lastInput.value = question
        sendQuestionToGemini(question)
    }

    private fun sendQuestionToGemini(question: String) {
        showLoading(true)

        geminiHelper.sendUserMessage(question) { responseText ->
            showLoading(false)
            if (responseText.isBlank()) showError("No response text received.")
            else {
                viewModel.responseHtml.value = responseText
                displayResponse(responseText)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !isLoading
        etQuestion.isEnabled = !isLoading
        btnSavePdf.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        runOnUiThread {
            showLoading(false)
            showToast("Error: $message")
        }
    }

    private fun displayResponse(htmlContent: String) {
        val formattedHtml = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>
                    body {
                        font-family: 'Roboto', sans-serif;
                        padding: 16px;
                        background-color: #ffffff;
                        color: #202124;
                        line-height: 1.6;
                    }
                    h1, h2, h3 {
                        color: #1a73e8;
                    }
                    pre, code {
                        background-color: #f5f5f5;
                        padding: 8px;
                        border-radius: 6px;
                        overflow-x: auto;
                        font-family: monospace;
                        font-size: 14px;
                    }
                    pre {
                        white-space: pre-wrap;
                    }
                    ul {
                        padding-left: 20px;
                    }
                    a {
                        color: #1a0dab;
                        text-decoration: none;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body>$htmlContent</body>
            </html>
        """.trimIndent()

        webViewResponse.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
        cardWebView.visibility = View.VISIBLE
    }

    private fun displayWelcomeMessage() {
        displayResponse("<h2>Welcome to Smrtask!</h2><p>Ask me anything—I'll respond with clean, readable answers.</p>")
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun exportChatToPdf() {
        val responseHtml = viewModel.responseHtml.value ?: ""
        if (responseHtml.isBlank()) {
            showToast("No content to export.")
            return
        }

        val plainText = android.text.Html.fromHtml(responseHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        val pdfPath = PdfHelper.exportToPdf(this, plainText)

        if (pdfPath.isNotEmpty()) {
            showToast("PDF saved at $pdfPath")
            openPdf(pdfPath)
        } else {
            showToast("Failed to save PDF.")
        }
    }

    private fun openPdf(pdfPath: String) {
        val pdfFile = File(pdfPath)
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", pdfFile)
        val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(pdfIntent)
        } catch (e: Exception) {
            showToast("No PDF viewer found.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}