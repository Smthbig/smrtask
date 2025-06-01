package com.smrtask

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.smrtask.databinding.ActivitySetupBinding

/**
 * Kotlin version of SetupActivity for initial user setup.
 * Handles API key input, interest preferences, theme, and permissions.
 * Applies modern Kotlin idioms and lifecycle-aware permission handling.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var sharedPreferences: SharedPreferences

    // Permission launcher for runtime permission requests
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required for full functionality", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)

        // Mask API Key input by default
        binding.etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        // Restore existing data
        binding.etApiKey.setText(sharedPreferences.getString("api_key", ""))
        binding.etInterests.setText(sharedPreferences.getString("user_interests", ""))

        // Show/hide API key toggle
        binding.btnToggleApiKeyVisibility.setOnClickListener { toggleApiKeyVisibility() }

        // Request permissions on load
        checkPermissions()

        // Save and continue
        binding.btnSave.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            val interests = binding.etInterests.text.toString().trim()

            when {
                apiKey.length < 20 -> {
                    Toast.makeText(this, "API Key looks too short", Toast.LENGTH_SHORT).show()
                }
                interests.isEmpty() -> {
                    Toast.makeText(this, "Please enter your interests", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    saveSetupData(apiKey, interests)
                    Toast.makeText(this, "Setup Complete", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    /**
     * Toggle visibility of API key input.
     */
    private fun toggleApiKeyVisibility() {
        val isPasswordHidden = binding.etApiKey.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD

        if (isPasswordHidden) {
            binding.etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.btnToggleApiKeyVisibility.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            binding.etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.btnToggleApiKeyVisibility.setImageResource(android.R.drawable.ic_secure)
        }

        binding.etApiKey.setSelection(binding.etApiKey.text?.length ?: 0)
    }

    /**
     * Apply dark or light theme based on saved preference.
     */
    private fun applySavedTheme() {
        val isDarkMode = getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    /**
     * Save API key and interests securely.
     */
    private fun saveSetupData(apiKey: String, interests: String) {
        sharedPreferences.edit().apply {
            putString("api_key", apiKey)
            putString("user_interests", interests)
            apply()
        }
    }

    /**
     * Request required permissions based on Android version.
     */
    private fun checkPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasStoragePermission() -> {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    /**
     * Check if legacy storage permissions are granted.
     */
    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}