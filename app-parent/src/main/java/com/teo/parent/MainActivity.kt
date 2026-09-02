package com.teo.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.teo.parent.nav.ParentNavHost
import com.teo.parent.ui.theme.ParentalControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParentalControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParentNavHost()
                }
            }
        }
    }
}
