package com.example.lldc.ui.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lldc.R
import com.example.lldc.network.ApiService
import com.example.lldc.network.RetrofitClient
import com.example.lldc.util.LrcFormatUtil
import com.example.lldc.util.SharedPrefsUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.EditText
import android.widget.ImageButton

class MatchFragment : Fragment() {

    // 用于存储观察API变化的回调函数
    private val apiChangeObserver = { updateApiService() }

    private lateinit var fabSelectFolder: FloatingActionButton
    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressContainer: LinearLayout
    private lateinit var apiService: ApiService

    override fun onDestroy() {
        super.onDestroy()
        // 移除API变化观察者
        RetrofitClient.removeObserver(apiChangeObserver)
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复显示时确保使用最新的API服务
        updateApiService()
    }

    // 更新API服务实例
    private fun updateApiService() {
        val oldApiService = if (::apiService.isInitialized) apiService else null
        apiService = RetrofitClient.getApiService(requireContext())

        // API服务实例变化检测已在SettingsFragment中处理，此处不重复提示

        // 记录API服务实例变化
        if (oldApiService !== apiService) {
            println("MatchFragment: API服务实例已更新")
            // 可以在这里添加任何需要的UI更新或提示
        }
    }
    
    // 时间轴偏移UI
    private lateinit var offsetEditText: EditText
    private lateinit var offsetUpButton: ImageButton
    private lateinit var offsetDownButton: ImageButton

    private val selectDirectoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireActivity().contentResolver.takePersistableUriPermission(uri, takeFlags)
                matchLyricsInDirectory(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_match, container, false)
        // 初始化API服务
        updateApiService()
        // 添加API变化观察者
        RetrofitClient.addObserver(apiChangeObserver)

        fabSelectFolder = view.findViewById(R.id.fab_select_folder)
        logTextView = view.findViewById(R.id.logTextView)
        statusTextView = view.findViewById(R.id.statusTextView)
        progressContainer = view.findViewById(R.id.progressContainer)
        
        // 初始化时间轴偏移UI
        offsetEditText = view.findViewById(R.id.offsetEditText)
        offsetUpButton = view.findViewById(R.id.offsetUpButton)
        offsetDownButton = view.findViewById(R.id.offsetDownButton)

        fabSelectFolder.setOnClickListener {
            startFilePicker()
        }
        
        setupOffsetControls()

        return view
    }
    
    private fun startFilePicker() {
        if (!::fabSelectFolder.isInitialized || !fabSelectFolder.isEnabled) {
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        selectDirectoryLauncher.launch(intent)
    }
    
    private fun setupOffsetControls() {
        offsetUpButton.setOnClickListener {
            updateOffset(100)
        }
        
        offsetDownButton.setOnClickListener {
            updateOffset(-100)
        }
    }

    private fun updateOffset(change: Int) {
        val currentOffset = offsetEditText.text.toString().toIntOrNull() ?: 0
        val newOffset = currentOffset + change
        offsetEditText.setText(newOffset.toString())
    }
    
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentText = logTextView.text.toString()
        logTextView.text = if (currentText.isEmpty()) {
            "[$timestamp] $message"
        } else {
            "$currentText\n[$timestamp] $message"
        }
    }

    private fun matchLyricsInDirectory(directoryUri: Uri) {
        lifecycleScope.launch {
            logTextView.text = "" // Clear previous logs
            addLog("开始扫描文件夹...")
            progressContainer.visibility = View.VISIBLE
            statusTextView.text = "正在扫描文件..."
            fabSelectFolder.isEnabled = false

            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), directoryUri)
            if (documentFile == null || !documentFile.isDirectory) {
                Toast.makeText(context, "无效的目录", Toast.LENGTH_SHORT).show()
                progressContainer.visibility = View.GONE
                fabSelectFolder.isEnabled = true
                return@launch
            }

            val audioFiles = withContext(Dispatchers.IO) {
                documentFile.listFiles().filter {
                    it.isFile && (it.name?.endsWith(".mp3", true) == true ||
                            it.name?.endsWith(".flac", true) == true ||
                            it.name?.endsWith(".m4a", true) == true ||
                            it.name?.endsWith(".ogg", true) == true)
                }
            }

            if (audioFiles.isEmpty()) {
                addLog("目录下没有找到支持的音频文件。")
                progressContainer.visibility = View.GONE
                fabSelectFolder.isEnabled = true
                return@launch
            }

            addLog("发现 ${audioFiles.size} 个音频文件，开始匹配...")
            var matchedCount = 0
            val failedFiles = mutableListOf<String>()

            for ((index, file) in audioFiles.withIndex()) {
                val fileName = file.name ?: "未知文件"
                withContext(Dispatchers.Main) {
                    statusTextView.text = "匹配中: ${index + 1}/${audioFiles.size}"
                }
                addLog("正在处理: $fileName")

                try {
                    withContext(Dispatchers.IO) {
                        val pfd = requireContext().contentResolver.openFileDescriptor(file.uri, "r")
                        val fd = pfd?.fileDescriptor

                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(fd)
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLongOrNull()
                        retriever.release()
                        pfd?.close()

                        val responseBody: ResponseBody? = if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                            apiService.matchLyrics(title, artist, duration)
                        } else {
                            val keyword = fileName.substringBeforeLast('.')
                            apiService.matchLyricsByKeyword(keyword, duration)
                        }

                        val lyrics = responseBody?.string()

                        if (lyrics != null && !lyrics.contains("No lyrics found")) {
                            writeLyricsToFile(file.uri, lyrics, fileName)
                            withContext(Dispatchers.Main) {
                                addLog(" -> 成功: $fileName")
                                matchedCount++
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                addLog(" -> 失败 (未找到歌词): $fileName")
                                failedFiles.add(fileName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                       addLog(" -> 错误: $fileName (${e.message})")
                       failedFiles.add("$fileName (错误)")
                    }
                }
            }

            progressContainer.visibility = View.GONE
            fabSelectFolder.isEnabled = true
            addLog("\n----- 匹配完成 -----")
            addLog("总共扫描 ${audioFiles.size} 个文件。")
            addLog("成功匹配 $matchedCount 个。")
            if (failedFiles.isNotEmpty()){
                addLog("失败 ${failedFiles.size} 个:\n${failedFiles.joinToString("\n")}")
            }
        }
    }

    private fun writeLyricsToFile(uri: Uri, lyrics: String, originalFileName: String) {
        try {
            // 检查是否启用增强型LRC
            val useEnhancedLrc = SharedPrefsUtil.read("use_enhanced_lrc", false)
            val offset = offsetEditText.text.toString().toIntOrNull() ?: 0
            
            // 处理歌词内容
            var finalLyrics = if (useEnhancedLrc) {
                // 只有在歌词不包含增强型标记时才进行转换
                LrcFormatUtil.convertToEnhancedLrc(lyrics)
            } else {
                lyrics
            }
            
            // 应用偏移
            if (offset != 0) {
                finalLyrics = LrcFormatUtil.applyOffsetToLrc(finalLyrics, offset)
            }
            
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val extension = originalFileName.substringAfterLast('.', "")
            val tempFile = File.createTempFile("temp_audio_", ".$extension", requireContext().cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            inputStream?.close()

            TagOptionSingleton.getInstance().isAndroid = false
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.LYRICS, finalLyrics)
            audioFile.commit()

            val outputStream = requireContext().contentResolver.openOutputStream(uri, "w")
            tempFile.inputStream().use {
                it.copyTo(outputStream!!)
            }
            outputStream?.close()
            tempFile.delete()

        } catch (e: Exception) {
            // Log exception to the on-screen log
            lifecycleScope.launch(Dispatchers.Main) {
                 addLog("!! 写入文件失败: $originalFileName - ${e.message}")
            }
        }
    }
}