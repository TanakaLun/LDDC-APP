package com.example.lldc.ui.fragment

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lldc.R
import com.example.lldc.data.Song
import com.example.lldc.network.ApiService
import com.example.lldc.network.RetrofitClient
import com.example.lldc.ui.adapter.SongAdapter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    // 用于存储观察API变化的回调函数
    private val apiChangeObserver = { updateApiService() }

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
            println("SearchFragment: API服务实例已更新")
            // 强制重新搜索，确保使用新的API设置
            if (lastSearchQuery.isNotEmpty()) {
                searchSongs()
            }
        }
    }

    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var sourceSelectButton: MaterialButton
    private lateinit var songsRecyclerView: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var apiService: ApiService
    
    // 音乐源选择
    private var selectedSources = mutableSetOf<String>()
    
    // 保存的搜索结果
    private var savedSongsList: List<Song> = emptyList()
    private var lastSearchQuery: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true // 这会使Fragment在配置更改（如旋转）时保留实例
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        // 初始化API服务
        updateApiService()
        // 添加API变化观察者
        RetrofitClient.addObserver(apiChangeObserver)

        searchEditText = view.findViewById(R.id.searchEditText)
        searchButton = view.findViewById(R.id.searchButton)
        sourceSelectButton = view.findViewById(R.id.sourceSelectButton)
        songsRecyclerView = view.findViewById(R.id.songsRecyclerView)

        // 恢复保存的状态
        if (savedInstanceState != null) {
            lastSearchQuery = savedInstanceState.getString("last_search_query", "")
            val sourcesArray = savedInstanceState.getStringArray("selected_sources")
            if (sourcesArray != null) {
                selectedSources.clear()
                selectedSources.addAll(sourcesArray)
            }
        }
        
        // 更新按钮状态
        updateSourceButtonState()
        
        // 初始化适配器
        songAdapter = SongAdapter(requireContext(), savedSongsList)
        songsRecyclerView.layoutManager = LinearLayoutManager(context)
        songsRecyclerView.adapter = songAdapter

        // 恢复搜索框文本
        if (lastSearchQuery.isNotEmpty()) {
            searchEditText.setText(lastSearchQuery)
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchSongs()
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener {
            searchSongs()
        }
        
        sourceSelectButton.setOnClickListener {
            showSourceSelectionDialog()
        }

        // 初始设置所有源
        if (selectedSources.isEmpty() && savedInstanceState == null) {
            selectedSources.clear() // 默认为空集合，表示所有源都选
        }

        return view
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存搜索查询
        outState.putString("last_search_query", lastSearchQuery)
        
        // 保存选定的源
        if (selectedSources.isNotEmpty()) {
            outState.putStringArray("selected_sources", selectedSources.toTypedArray())
        }
    }
    
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // 如果有保存的搜索结果，则恢复它们
        if (savedSongsList.isNotEmpty()) {
            songAdapter.updateSongs(savedSongsList)
        }
        // 如果需要重新搜索，可以在这里调用
        // 但通常不需要，因为我们已经保存了结果
    }
    
    private fun showSourceSelectionDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_source_selection)
        
        // 使用标准CheckBox
        val checkBoxQM = dialog.findViewById<CheckBox>(R.id.checkBoxQM)
        val checkBoxKG = dialog.findViewById<CheckBox>(R.id.checkBoxKG)
        val checkBoxNE = dialog.findViewById<CheckBox>(R.id.checkBoxNE)
        val checkBoxKW = dialog.findViewById<CheckBox>(R.id.checkBoxKW)
        
        // 设置当前选择状态
        checkBoxQM.isChecked = selectedSources.contains("qm") || selectedSources.isEmpty()
        checkBoxKG.isChecked = selectedSources.contains("kg") || selectedSources.isEmpty()
        checkBoxNE.isChecked = selectedSources.contains("ne") || selectedSources.isEmpty()
        if (checkBoxKW != null) {
            checkBoxKW.isChecked = selectedSources.contains("kw") || selectedSources.isEmpty()
        }

        // 添加文字点击也可以触发对应复选框切换
        val textQM = (checkBoxQM.parent as ViewGroup).getChildAt(1) as TextView
        textQM.setOnClickListener {
            checkBoxQM.isChecked = !checkBoxQM.isChecked
        }
        
        val textKG = (checkBoxKG.parent as ViewGroup).getChildAt(1) as TextView
        textKG.setOnClickListener {
            checkBoxKG.isChecked = !checkBoxKG.isChecked
        }
        
        val textNE = (checkBoxNE.parent as ViewGroup).getChildAt(1) as TextView
        textNE.setOnClickListener {
            checkBoxNE.isChecked = !checkBoxNE.isChecked
        }
        
        val textKW = checkBoxKW?.let { (it.parent as ViewGroup).getChildAt(1) as? TextView }
        textKW?.setOnClickListener {
            checkBoxKW.isChecked = !checkBoxKW.isChecked
        }
        
        dialog.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            selectedSources.clear()
            
            if (checkBoxQM.isChecked) selectedSources.add("qm")
            if (checkBoxKG.isChecked) selectedSources.add("kg")
            if (checkBoxNE.isChecked) selectedSources.add("ne")
            if (checkBoxKW != null && checkBoxKW.isChecked) selectedSources.add("kw")
            
            // 如果什么都没选，则默认全选（使用空集合表示）
            if (selectedSources.isEmpty() || selectedSources.size == 4) {
                selectedSources.clear()
            }
            
            // 更新按钮状态并立即进行搜索
            updateSourceButtonState()
            
            // 如果搜索框有内容，则立即搜索
            if (searchEditText.text.toString().trim().isNotEmpty()) {
                searchSongs()
            }
            
            dialog.dismiss()
        }
        
        // 设置对话框宽度为屏幕宽度的85%
        val window = dialog.window
        window?.let {
            val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            val params = it.attributes
            params.width = width
            it.attributes = params
        }
        
        dialog.show()
    }
    
    private fun updateSourceButtonState() {
        // 如果没有选中任何源或全选了，图标保持默认
        if (selectedSources.isEmpty() || selectedSources.size == 4) {
            sourceSelectButton.setIconTint(ColorStateList.valueOf(Color.parseColor("#673AB7")))
        } else {
            // 部分选中时，使用紫色
            sourceSelectButton.setIconTint(ColorStateList.valueOf(Color.parseColor("#673AB7")))
            // 设置背景着色
            sourceSelectButton.strokeColor = ColorStateList.valueOf(Color.parseColor("#673AB7"))
        }
    }

    private fun searchSongs() {
        val query = searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(context, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存当前的搜索查询
        lastSearchQuery = query
        
        // 构建源参数
        val sourcesParam = if (selectedSources.isEmpty()) {
            null  // 空集合表示全选，传null
        } else {
            selectedSources.joinToString(",")
        }
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.searchSongs(query, sourcesParam)
                }
                if (response.isSuccessful) {
                    val songs = response.body() ?: emptyList()
                    savedSongsList = songs // 保存搜索结果
                    songAdapter.updateSongs(songs)
                } else {
                    Toast.makeText(context, "搜索失败: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}