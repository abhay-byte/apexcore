package com.ivarna.apexcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.ui.shell.MainScreen
import com.ivarna.apexcore.ui.theme.ApexCoreTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val gameManager by lazy { GameManager(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreezeFramework.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ApexCoreTheme {
                MainScreen(gameManager)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        FreezeFramework.resolver()?.invalidate()
        // Re-detect so chip/banner refresh immediately after granting Shizuku/Root
        lifecycleScope.launch {
            FreezeFramework.detect()
        }
    }
}
