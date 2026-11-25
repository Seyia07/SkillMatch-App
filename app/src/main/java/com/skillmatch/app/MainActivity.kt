package com.skillmatch.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.skillmatch.app.ui.online.OnlineViewModel
import com.skillmatch.app.ui.online.SkillMatchOnlineApp
import com.skillmatch.app.ui.theme.SkillMatchTheme

class MainActivity : ComponentActivity() {
    private val vm: OnlineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // White status-bar icons over the app's plum strip; dark icons on the white nav bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        setContent {
            SkillMatchTheme { SkillMatchOnlineApp(vm) }
        }
    }
}
