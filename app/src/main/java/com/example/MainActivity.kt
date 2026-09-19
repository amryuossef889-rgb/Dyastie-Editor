package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.dyastie.ui.DyastieEditorScreen
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.ui.theme.DyastieTheme
import com.example.ui.theme.NleBackground

class MainActivity : ComponentActivity() {
    private val viewModel: DyastieViewModel by lazy {
        ViewModelProvider(this)[DyastieViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DyastieTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NleBackground
                ) {
                    DyastieEditorScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveStateOnAppPause()
        viewModel.playback.pause(viewModel.project.value)
    }
}
