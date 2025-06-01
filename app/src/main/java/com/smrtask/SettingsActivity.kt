package com.smrtask

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences

    // Views
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etModelId: TextInputEditText
    private lateinit var sliderTemperature: Slider
    private lateinit var tvTemperatureValue: TextView
    private lateinit var etMaxTokens: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var switchDarkMode: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPrefs = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)

        // Toolbar
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        // Init views
        etApiKey = findViewById(R.id.etApiKey)
        etModelId = findViewById(R.id.etModelIdentifier)
        sliderTemperature = findViewById(R.id.sliderTemperature)
        tvTemperatureValue = findViewById(R.id.tvTemperatureValue)
        etMaxTokens = findViewById(R.id.etMaxOutputTokens)
        btnSave = findViewById(R.id.btnSaveApiKey)
        switchDarkMode = findViewById(R.id.switchDarkMode)

        // Load saved preferences
        etApiKey.setText(sharedPrefs.getString("api_key", ""))
        etModelId.setText(sharedPrefs.getString("model_id", "gemini-2.5-flash-preview-04-17"))
        sliderTemperature.value = sharedPrefs.getFloat("temperature", 0.5f)
        tvTemperatureValue.text = "Temperature: ${sliderTemperature.value}"
        etMaxTokens.setText(sharedPrefs.getInt("max_tokens", 8192).toString())
        switchDarkMode.isChecked = isDarkMode()

        // Update label while dragging
        sliderTemperature.addOnChangeListener { _, value, _ ->
            tvTemperatureValue.text = "Temperature: $value"
        }

        // Save settings
        btnSave.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val modelId = etModelId.text.toString().trim()
            val maxTokens = etMaxTokens.text.toString().toIntOrNull() ?: 8192
            val temperature = sliderTemperature.value

            if (apiKey.length < 20) {
                Toast.makeText(this, "API key is too short!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sharedPrefs.edit()
                .putString("api_key", apiKey)
                .putString("model_id", modelId.ifEmpty { "gemini-2.5-flash-preview-04-17" })
                .putFloat("temperature", temperature)
                .putInt("max_tokens", maxTokens)
                .apply()

            Toast.makeText(this, "Configuration saved.", Toast.LENGTH_SHORT).show()
        }

        // Dark mode toggle
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            saveThemeSetting(isChecked)
            applyTheme(isChecked)
        }
    }

    private fun applySavedTheme() {
        val isDark = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun saveThemeSetting(isDarkMode: Boolean) {
        sharedPrefs.edit().putBoolean("dark_mode", isDarkMode).apply()
    }

    private fun applyTheme(isDarkMode: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun isDarkMode(): Boolean {
        return sharedPrefs.getBoolean("dark_mode", false)
    }
}