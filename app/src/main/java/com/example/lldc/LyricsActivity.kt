package com.example.lldc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.lldc.R
import com.example.lldc.network.ApiService
import com.example.lldc.network.RetrofitClient
import com.example.lldc.util.LrcFormatUtil
import com.example.lldc.util.SharedPrefsUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent

class LyricsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONG_INFO_JSON = "songInfoJson"
        const val EXTRA_SONG_TITLE = "title"
        const val EXTRA_SONG_ARTIST = "artist"
    }

    private lateinit var lyricsTextView: TextView
    private lateinit var offsetControlContainer: LinearLayout
    private lateinit var offsetEditText: EditText
    private lateinit var saveLyricsButton: Button
    private lateinit var embedLyricsButton: Button
    private lateinit var copyLyricsButton: Button


    private var songInfoJson: String = ""
    private var songTitle: String = ""
    private var songArtist: String = ""
    private var originalLyrics: String = ""
    private var offset: Int = 0

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                val lrcContent = applyOffsetAndEnhance(lyricsTextView.text.toString())
                embedLyricsInAudioFile(uri, lrcContent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.lyricsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化所有UI组件
        initializeViews()

        songInfoJson = intent.getStringExtra(EXTRA_SONG_INFO_JSON) ?: ""
        songTitle = intent.getStringExtra(EXTRA_SONG_TITLE) ?: ""
        songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST) ?: ""
        supportActionBar?.title = songTitle.ifEmpty { "歌词" }

        if (songInfoJson.isEmpty()) {
            Toast.makeText(this, "歌曲信息缺失", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchLyrics(songInfoJson)

        setupOffsetControls()

        saveLyricsButton.setOnClickListener {
            saveLyrics()
        }

        embedLyricsButton.setOnClickListener {
            embedLyrics()
        }

        copyLyricsButton.setOnClickListener {
            copyLyricsToClipboard()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.lyrics_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_toggle_offset -> {
                toggleOffsetControls()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchLyrics(songInfoJson: String) {
        lyricsTextView.text = "正在加载歌词..."
        Log.d("LyricsActivity", "Fetching lyrics with song_info_json: $songInfoJson")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getApiService(this@LyricsActivity).getLyrics(songInfoJson)
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string()
                    runOnUiThread {
                        val lrcText = responseBody ?: "未找到歌词"
                        originalLyrics = lrcText
                        
                        val useEnhancedLrc = SharedPrefsUtil.read("use_enhanced_lrc", false)
                        val displayedLrc = if (useEnhancedLrc) {
                            LrcFormatUtil.convertToEnhancedLrc(lrcText)
                        } else {
                            lrcText
                        }
                        
                        lyricsTextView.text = displayedLrc
                        Log.d("LyricsActivity", "Lyrics fetched successfully")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("LyricsActivity", "Failed to fetch lyrics: ${response.code()} - $errorBody")
                    runOnUiThread { lyricsTextView.text = "加载歌词失败: ${response.code()}" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("LyricsActivity", "Error fetching lyrics", e)
                runOnUiThread { lyricsTextView.text = "加载歌词失败: ${e.message}" }
            }
        }
    }

    private fun saveLyrics() {
        if (!::lyricsTextView.isInitialized || !::saveLyricsButton.isInitialized) return
        
        val lrcContent = lyricsTextView.text.toString()
        if (lrcContent.isBlank()) {
            Toast.makeText(this, "没有歌词可以保存", Toast.LENGTH_SHORT).show()
            return
        }

        val title = songTitle.ifEmpty { "lyrics" }
        val artist = songArtist.ifEmpty { "unknown" }
        val fileName = "$title - $artist.lrc"
        
        val finalLrc = applyOffsetAndEnhance(lrcContent)

        val useDefaultFilename = SharedPrefsUtil.read("use_default_filename", false)

        if (useDefaultFilename) {
            saveLrcFile(fileName, finalLrc)
        } else {
            showSaveDialog(fileName, finalLrc)
        }
    }

    private fun embedLyrics() {
        if (!::lyricsTextView.isInitialized || !::embedLyricsButton.isInitialized) return
        
        val lrcContent = lyricsTextView.text.toString()
        if (lrcContent.isBlank()) {
            Toast.makeText(this, "没有歌词可以内嵌", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        pickFileLauncher.launch(intent)
    }

    private fun toggleOffsetControls() {
        if (!::offsetControlContainer.isInitialized) return
        
        val isVisible = offsetControlContainer.visibility == View.VISIBLE
        offsetControlContainer.visibility = if (isVisible) View.GONE else View.VISIBLE

        if (!isVisible) {
            Toast.makeText(this, "调整时间轴", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyOffsetAndEnhance(lrcContent: String): String {
        val currentOffset = try {
            offsetEditText.text.toString().toIntOrNull() ?: offset
        } catch (e: Exception) {
            offset
        }
        
        val useEnhancedLrc = SharedPrefsUtil.read("use_enhanced_lrc", false)
        return if (useEnhancedLrc) {
            val enhancedLrc = LrcFormatUtil.convertToEnhancedLrc(lrcContent)
            LrcFormatUtil.applyOffsetToLrc(enhancedLrc, currentOffset)
        } else {
            LrcFormatUtil.applyOffsetToLrc(lrcContent, currentOffset)
        }
    }

    private fun saveLrcFile(fileName: String, lrcContent: String) {
        val lrcDirUri = SharedPrefsUtil.read("lrc_save_path", null)
        if (lrcDirUri != null) {
            saveLyricsToUri(Uri.parse(lrcDirUri), fileName, lrcContent)
        } else {
            saveLyricsToDownloads(fileName, lrcContent)
        }
    }

    private fun showSaveDialog(defaultFileName: String, lrcContent: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_lyrics, null)
        val fileNameEditText = dialogView.findViewById<EditText>(R.id.fileNameEditText)
        fileNameEditText.setText(defaultFileName)

        AlertDialog.Builder(this)
            .setTitle("保存歌词")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val fileName = fileNameEditText.text.toString()
                saveLrcFile(fileName, lrcContent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveLyricsToUri(directoryUri: Uri, fileName: String, lrcContent: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(this@LyricsActivity, directoryUri)
                if (directory == null || !directory.canWrite()) {
                    runOnUiThread { Toast.makeText(this@LyricsActivity, "无法写入指定目录", Toast.LENGTH_LONG).show() }
                    return@launch
                }
                val sanitizedFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = directory.createFile("application/octet-stream", sanitizedFileName)
                if (file == null) {
                    runOnUiThread { Toast.makeText(this@LyricsActivity, "创建文件失败", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                contentResolver.openOutputStream(file.uri)?.use { it.write(lrcContent.toByteArray()) }
                runOnUiThread { Toast.makeText(this@LyricsActivity, "歌词已保存到: $sanitizedFileName", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@LyricsActivity, "保存失败: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun saveLyricsToDownloads(fileName: String, lrcContent: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        try {
            FileOutputStream(file).use { it.write(lrcContent.toByteArray()) }
            Toast.makeText(this, "歌词已保存到 'Downloads' 文件夹", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("LyricsActivity", "Error saving lyrics to Downloads", e)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        result = cursor.getString(displayNameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            result?.let { res ->
                val cut = res.lastIndexOf('/')
                if (cut != -1) {
                    result = res.substring(cut + 1)
                }
            }
        }
        return result
    }

    private fun embedLyricsInAudioFile(audioFileUri: Uri, lrcContent: String) {
        var tempFile: File? = null
        try {
            val originalFileName = getFileName(audioFileUri)
            val extension = originalFileName?.substringAfterLast('.', "") ?: ""
            val suffix = if (extension.isNotEmpty()) ".$extension" else ".tmp"
            tempFile = File.createTempFile("lrc_embed_", suffix, cacheDir)

            contentResolver.openInputStream(audioFileUri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val audioFile = AudioFileIO.read(tempFile)
            val tag: Tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.LYRICS, lrcContent)
            audioFile.commit()

            contentResolver.openOutputStream(audioFileUri, "w")?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(this, "歌词成功内嵌", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("LyricsActivity", "内嵌歌词失败", e)
            Toast.makeText(this, "内嵌歌词失败: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            tempFile?.delete()
        }
    }
    
    private fun setupOffsetControls() {
        if (!::offsetEditText.isInitialized) return
        
        val offsetDownButton: ImageButton = findViewById(R.id.offsetDownButton)
        val offsetUpButton: ImageButton = findViewById(R.id.offsetUpButton)

        offsetDownButton.setOnClickListener {
            offset -= 100
            offsetEditText.setText(offset.toString())
            updateDisplayedLyrics()
        }

        offsetUpButton.setOnClickListener {
            offset += 100
            offsetEditText.setText(offset.toString())
            updateDisplayedLyrics()
        }

        offsetEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                offset = v.text.toString().toIntOrNull() ?: 0
                updateDisplayedLyrics()
                true
            } else {
                false
            }
        }
    }

    private fun updateDisplayedLyrics() {
        if (!::lyricsTextView.isInitialized) return
        
        if (originalLyrics.isNotBlank()) {
            lyricsTextView.text = applyOffsetAndEnhance(originalLyrics)
        }
    }

    private fun copyLyricsToClipboard() {
        if (!::lyricsTextView.isInitialized || !::copyLyricsButton.isInitialized) return
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("lyrics", lyricsTextView.text.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "歌词已复制", Toast.LENGTH_SHORT).show()
    }

    private fun initializeViews() {
        try {
            lyricsTextView = findViewById(R.id.lyricsTextView)
            offsetControlContainer = findViewById(R.id.offsetControlContainer)
            offsetEditText = findViewById(R.id.offsetEditText)
            saveLyricsButton = findViewById(R.id.saveLyricsButton)
            embedLyricsButton = findViewById(R.id.embedLyricsButton)
            copyLyricsButton = findViewById(R.id.copyLyricsButton)
            
            // 设置初始值
            offsetEditText.setText("0")
        } catch (e: Exception) {
            Log.e("LyricsActivity", "初始化UI组件失败", e)
            Toast.makeText(this, "初始化界面失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 