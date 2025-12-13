package com.example.lldc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lldc.ui.fragment.MatchFragment
import com.example.lldc.ui.fragment.SearchFragment
import com.example.lldc.ui.fragment.SettingsFragment
import com.example.lldc.util.SharedPrefsUtil
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavView: BottomNavigationView
    
    // 存储不同的Fragment实例
    private val fragmentMap = mutableMapOf<Int, Fragment>()
    private var activeFragmentId = R.id.navigation_search
    
    // Fragment标签名称
    private val SEARCH_FRAGMENT_TAG = "search_fragment"
    private val MATCH_FRAGMENT_TAG = "match_fragment"
    private val SETTINGS_FRAGMENT_TAG = "settings_fragment"
    
    // 存储权限请求
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Android 11+ 所有文件访问权限结果
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "没有获得所有文件访问权限，某些功能可能受限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化SharedPreferences工具
        SharedPrefsUtil.init(this)

        bottomNavView = findViewById(R.id.bottom_nav_view)

        // 初始化或恢复Fragment
        if (savedInstanceState == null) {
            // 首次创建，初始化所有Fragment
            initFragments()
        } else {
            // 恢复状态
            restoreFragments(savedInstanceState)
        }

        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_search -> {
                    switchFragment(R.id.navigation_search)
                    true
                }
                R.id.navigation_match -> {
                    switchFragment(R.id.navigation_match)
                    true
                }
                R.id.navigation_settings -> {
                    switchFragment(R.id.navigation_settings)
                    true
                }
                else -> false
            }
        }

        // 如果是首次创建，设置默认Fragment
        if (savedInstanceState == null) {
            bottomNavView.selectedItemId = R.id.navigation_search
        } else {
            // 恢复选中的底部导航项
            bottomNavView.selectedItemId = activeFragmentId
        }
        
        // 检查并请求存储权限
        checkStoragePermissions()
    }
    
    private fun initFragments() {
        // 创建所有Fragment实例
        fragmentMap[R.id.navigation_search] = SearchFragment()
        fragmentMap[R.id.navigation_match] = MatchFragment()
        fragmentMap[R.id.navigation_settings] = SettingsFragment()
        
        // 初始添加所有Fragment，但隐藏它们
        val transaction = supportFragmentManager.beginTransaction()
        
        transaction.add(R.id.nav_host_fragment, fragmentMap[R.id.navigation_search]!!, SEARCH_FRAGMENT_TAG)
        transaction.add(R.id.nav_host_fragment, fragmentMap[R.id.navigation_match]!!, MATCH_FRAGMENT_TAG)
        transaction.add(R.id.nav_host_fragment, fragmentMap[R.id.navigation_settings]!!, SETTINGS_FRAGMENT_TAG)
        
        // 默认情况下隐藏除了搜索之外的所有Fragment
        transaction.hide(fragmentMap[R.id.navigation_match]!!)
        transaction.hide(fragmentMap[R.id.navigation_settings]!!)
        
        transaction.commit()
    }
    
    private fun restoreFragments(savedInstanceState: Bundle) {
        // 从FragmentManager恢复Fragment
        val searchFragment = supportFragmentManager.findFragmentByTag(SEARCH_FRAGMENT_TAG)
        val matchFragment = supportFragmentManager.findFragmentByTag(MATCH_FRAGMENT_TAG)
        val settingsFragment = supportFragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG)
        
        // 将恢复的Fragment保存到Map中
        if (searchFragment != null) fragmentMap[R.id.navigation_search] = searchFragment
        if (matchFragment != null) fragmentMap[R.id.navigation_match] = matchFragment
        if (settingsFragment != null) fragmentMap[R.id.navigation_settings] = settingsFragment
        
        // 恢复活跃的Fragment ID
        activeFragmentId = savedInstanceState.getInt("active_fragment_id", R.id.navigation_search)
    }
    
    private fun switchFragment(fragmentId: Int) {
        if (fragmentId == activeFragmentId) {
            return
        }
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // 隐藏当前活跃的Fragment
        fragmentMap[activeFragmentId]?.let { transaction.hide(it) }
        
        // 显示要切换到的Fragment
        fragmentMap[fragmentId]?.let {
            transaction.show(it)
            activeFragmentId = fragmentId
        }
        
        transaction.commit()
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                // 检查是否已经请求过权限，避免重复询问
                val hasAskedForPermission = SharedPrefsUtil.read("has_asked_for_all_files_permission", false)
                
                if (!hasAskedForPermission) {
                    showAllFilesAccessDialog()
                    // 标记为已经询问过
                    SharedPrefsUtil.write("has_asked_for_all_files_permission", true)
                }
            }
        } else {
            // Android 10 及以下使用传统存储权限
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            
            if (!hasPermissions(permissions)) {
                // 检查是否已经请求过权限，避免重复询问
                val hasAskedForPermission = SharedPrefsUtil.read("has_asked_for_storage_permission", false)
                
                if (!hasAskedForPermission) {
                    storagePermissionLauncher.launch(permissions)
                    // 标记为已经询问过
                    SharedPrefsUtil.write("has_asked_for_storage_permission", true)
                }
            }
        }
    }
    
    private fun showAllFilesAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("为了正常内嵌歌词到音乐文件中，需要授予应用管理所有文件的权限")
            .setPositiveButton("设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    allFilesAccessLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        allFilesAccessLauncher.launch(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "无法打开权限设置页面", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }
    
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存当前活跃的Fragment ID
        outState.putInt("active_fragment_id", activeFragmentId)
    }
}