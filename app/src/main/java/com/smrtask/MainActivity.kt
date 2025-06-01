package com.smrtask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
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

/**
 * MainActivity: Core UI for Smrtask app.
 * Handles question input, Gemini interaction, and result rendering with PDF export and theming.
 */
class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var etQuestion: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSavePdf: MaterialButton
    private lateinit var webViewResponse: WebView
    private lateinit var cardWebView: MaterialCardView
    private lateinit var progressLoading: ProgressBar

    // Gemini helper to interact with AI model
    private lateinit var geminiHelper: GeminiHelper

    // ViewModel to preserve UI state across configuration changes
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme() // Apply theme before super.onCreate to avoid flicker
        super.onCreate(savedInstanceState)

        // 🚀 Check if API key is missing and redirect to SetupActivity
        if (isFirstTimeSetupRequired()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        setupViews()
        setupWebView()
        setupGeminiHelper()

        // Restore previous UI state from ViewModel
        viewModel.responseHtml.value?.let { displayResponse(it) }
        viewModel.lastInput.value?.let { etQuestion.setText(it) }

        if (viewModel.chatHistory.isEmpty()) displayWelcomeMessage()
    }

    /**
     * Applies saved dark mode setting using SharedPreferences
     */
    private fun applySavedTheme() {
        val prefs = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    /**
     * Checks if API key is missing (first-time setup)
     */
    private fun isFirstTimeSetupRequired(): Boolean {
        val prefs = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", null)
        return apiKey.isNullOrBlank()
    }

    /**
     * Finds and sets up all view references and listeners
     */
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

    /**
     * Configures the WebView to properly display HTML responses
     */
    private fun setupWebView() {
        webViewResponse.webViewClient = WebViewClient()
        webViewResponse.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webViewResponse.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Initializes GeminiHelper with error and UI callback handlers
     */
    private fun setupGeminiHelper() {
        geminiHelper = GeminiHelper(
            context = this,
            errorHandler = { errorMessage -> showError(errorMessage) },
            uiCallback = { runnable -> runOnUiThread(runnable) }
        )
    }

    private fun onSendClicked() {
        val question = etQuestion.text?.toString()?.trim().orEmpty()
        if (question.isEmpty()) {
            showToast("Please enter a question.")
            return
        }
        viewModel.lastInput.value = question
        sendQuestionToGemini(question)
    }

    private fun sendQuestionToGemini(question: String) {
        showLoading(true)
        geminiHelper.sendUserMessage(question) { responseText ->
            runOnUiThread {
                showLoading(false)
                if (responseText.isNullOrBlank()) {
                    showError("Empty response received.")
                } else {
                    viewModel.responseHtml.value = responseText
                    displayResponse(responseText)
                }
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
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <style>
                    @import url('https://fonts.googleapis.com/css2?family=Roboto&display=swap');
                    body {
                        font-family: 'Roboto', sans-serif;
                        padding: 16px;
                        background-color: #ffffff;
                        color: #202124;
                        line-height: 1.6;
                        margin: 0;
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
            <body>${htmlContent.trim()}</body>
            </html>
        """.trimIndent()

        webViewResponse.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
        cardWebView.visibility = View.VISIBLE
        webViewResponse.postDelayed({ webViewResponse.pageDown(true) }, 300)
    }

    private fun displayWelcomeMessage() {
        displayResponse(
            "<h2>Welcome to Smrtask!</h2>" +
            "<p>Ask me anything—I'll respond with clean, readable answers.</p>"
        )
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun exportChatToPdf() {
        val htmlContent = viewModel.responseHtml.value
        if (htmlContent.isNullOrBlank()) {
            showToast("No content to export.")
            return
        }

        val pdfPath = PdfHelper.exportToPdf(this, htmlContent)

        if (!pdfPath.isNullOrEmpty()) {
            showToast("PDF saved at $pdfPath")
            openPdf(pdfPath)
        } else {
            showToast("Failed to save PDF.")
        }
    }

    private fun openPdf(pdfPath: String) {
        try {
            val pdfFile = File(pdfPath)
            val pdfUri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                pdfFile
            )

            val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (pdfIntent.resolveActivity(packageManager) != null) {
                startActivity(pdfIntent)
            } else {
                showToast("No PDF viewer found.")
            }
        } catch (e: Exception) {
            showToast("Unable to open PDF: ${e.message}")
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}