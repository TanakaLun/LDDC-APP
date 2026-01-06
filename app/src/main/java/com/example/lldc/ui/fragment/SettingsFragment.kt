package com.example.lldc.ui.fragment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.example.lldc.network.RetrofitClient
import androidx.fragment.app.Fragment
import com.example.lldc.AboutActivity
import com.example.lldc.R
import com.example.lldc.util.SharedPrefsUtil
import com.google.android.material.materialswitch.MaterialSwitch
import android.widget.Toast

class SettingsFragment : Fragment() {

    private lateinit var apiSettingsLayout: LinearLayout
    private lateinit var lyricsPathLayout: LinearLayout
    private lateinit var aboutLayout: LinearLayout

    private lateinit var apiServerUrlSummary: TextView
    private lateinit var useDefaultFilenameSwitch: materialSwitch
    private lateinit var useEnhancedLrcSwitch: materialSwitch
    private lateinit var lyricsPathTextView: TextView
    private lateinit var filePickerSpinner: Spinner

    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireActivity().contentResolver.takePersistableUriPermission(it, takeFlags)
            
            // 获取并保存文件夹名称
            val docFile = DocumentFile.fromTreeUri(requireContext(), it)
            val dirName = docFile?.name ?: "无法解析路径"
            
            SharedPrefsUtil.write("lyrics_save_path", it.toString())
            SharedPrefsUtil.write("lyrics_save_path_name", dirName) // 同时保存名称
            
            lyricsPathTextView.text = dirName // 直接更新UI
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize views
        apiSettingsLayout = view.findViewById(R.id.apiSettingsLayout)
        lyricsPathLayout = view.findViewById(R.id.lyricsPathLayout)
        aboutLayout = view.findViewById(R.id.aboutLayout)
        apiServerUrlSummary = view.findViewById(R.id.apiServerUrlSummary)
        useDefaultFilenameSwitch = view.findViewById(R.id.useDefaultFilenameSwitch)
        useEnhancedLrcSwitch = view.findViewById(R.id.useEnhancedLrcSwitch)
        lyricsPathTextView = view.findViewById(R.id.lyricsPathTextView)
        filePickerSpinner = view.findViewById(R.id.filePickerSpinner)

        setupClickListeners()
        setupFilePickerSpinner()
        loadAndDisplaySettings()


        return view
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplaySettings()
    }

    private fun setupClickListeners() {
        apiSettingsLayout.setOnClickListener {
            showApiUrlDialog()
        }

        useDefaultFilenameSwitch.setOnCheckedChangeListener { _, isChecked ->
            SharedPrefsUtil.write("use_default_filename", isChecked)
        }

        useEnhancedLrcSwitch.setOnCheckedChangeListener { _, isChecked ->
            SharedPrefsUtil.write("use_enhanced_lrc", isChecked)
            Toast.makeText(requireContext(), if (isChecked) "已启用增强型LRC格式" else "已禁用增强型LRC格式", Toast.LENGTH_SHORT).show()
        }

        lyricsPathLayout.setOnClickListener {
            directoryPickerLauncher.launch(null)
        }

        aboutLayout.setOnClickListener {
            startActivity(Intent(activity, AboutActivity::class.java))
        }
    }

    private fun setupFilePickerSpinner() {
        val choices = arrayOf("系统默认", "MT管理器")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, choices).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        filePickerSpinner.adapter = adapter

        filePickerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val currentSelection = SharedPrefsUtil.read("file_picker_choice", "default")
                val newSelection = if (position == 1) "mt_manager" else "default"
                
                // 只有当用户实际改变选择时才更新
                if (currentSelection != newSelection) {
                    SharedPrefsUtil.write("file_picker_choice", newSelection)
                    
                    // 如果用户切换到MT管理器，重置权限请求标志，以便应用不会再请求权限
                    if (newSelection == "mt_manager") {
                        Toast.makeText(requireContext(), "已选择使用MT管理器作为文件选择器", Toast.LENGTH_SHORT).show()
                        resetPermissionRequestFlags()
                    } else {
                        Toast.makeText(requireContext(), "已选择使用系统默认文件选择器", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    /**
     * 重置权限请求标志，以便应用在需要时可以再次请求权限
     */
    private fun resetPermissionRequestFlags() {
        SharedPrefsUtil.write("has_asked_for_all_files_permission", false)
        SharedPrefsUtil.write("has_asked_for_storage_permission", false)
    }

    private fun loadAndDisplaySettings() {
        // API Server URL
        apiServerUrlSummary.text = SharedPrefsUtil.read("api_url", "http://127.0.0.1:8000/")

        // Use Default Filename
        useDefaultFilenameSwitch.isChecked = SharedPrefsUtil.read("use_default_filename", false)
        
        // Use Enhanced LRC
        useEnhancedLrcSwitch.isChecked = SharedPrefsUtil.read("use_enhanced_lrc", false)

        // Lyrics Save Path
        val lyricsPathName = SharedPrefsUtil.read("lyrics_save_path_name", null)
        if (lyricsPathName != null) {
            lyricsPathTextView.text = lyricsPathName
        } else {
            lyricsPathTextView.text = "未设置"
        }

        // File Picker Preference
        val currentChoice = SharedPrefsUtil.read("file_picker_choice", "default")
        filePickerSpinner.setSelection(if (currentChoice == "mt_manager") 1 else 0)
    }

    private fun showApiUrlDialog() {
        val editText = EditText(requireContext()).apply {
            setText(SharedPrefsUtil.read("api_url", ""))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("设置API服务器地址")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val newUrl = editText.text.toString()
                SharedPrefsUtil.write("api_url", newUrl)
                apiServerUrlSummary.text = newUrl
                // 重置Retrofit客户端，使新的API URL生效
                RetrofitClient.reset()
                // 手动触发API服务获取，确保观察者收到通知
                RetrofitClient.getApiService(requireContext())
                // 显示API更新提示
                Toast.makeText(requireContext(), "API设置已更新，使用新配置", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateLyricsPathView(uri: Uri) {
        val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
        lyricsPathTextView.text = docFile?.name ?: "无法解析路径"
    }
}