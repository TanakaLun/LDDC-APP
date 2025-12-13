package com.example.lldc

import android.os.Bundle
import android.text.util.Linkify
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // 强制设置状态栏图标为深色
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val toolbar: Toolbar = findViewById(R.id.aboutToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "关于"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 使链接可以点击
        val githubLinkTextView: TextView = findViewById(R.id.githubLinkTextView)
        Linkify.addLinks(githubLinkTextView, Linkify.WEB_URLS)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 