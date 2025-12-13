package com.example.lldc

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat

class ApiSettingsActivity : AppCompatActivity() {

    private lateinit var apiUrlEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_settings)

        // 强制设置状态栏图标为深色
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar: Toolbar = findViewById(R.id.apiSettingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "API 服务器地址"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        apiUrlEditText = findViewById(R.id.apiUrlEditText)
        saveButton = findViewById(R.id.saveButton)

        // Load saved URL
        val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentApiUrl = sharedPrefs.getString("api_url", "http://127.0.0.1:8000/")
        apiUrlEditText.setText(currentApiUrl)

        // Save button listener
        saveButton.setOnClickListener {
            val newApiUrl = apiUrlEditText.text.toString().trim()
            if (newApiUrl.isNotEmpty() && (newApiUrl.startsWith("http://") || newApiUrl.startsWith("https://"))) {
                sharedPrefs.edit().putString("api_url", newApiUrl).apply()
                Toast.makeText(this, "地址已保存", Toast.LENGTH_SHORT).show()
                finish() // Close settings activity
            } else {
                Toast.makeText(this, "请输入有效的URL地址", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 