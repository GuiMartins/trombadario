package com.trombadario

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.trombadario.ui.components.LocalWindowWidthSizeClass
import com.trombadario.ui.navigation.AppNavigation
import com.trombadario.ui.theme.DeskBackground
import com.trombadario.ui.theme.NotebookBackground
import com.trombadario.ui.theme.ThemePreference
import com.trombadario.ui.theme.TrombadarioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocks screenshots, screen recording and adb screencap, and blanks the
        // app's thumbnail in the recents list. On in every build type, debug
        // included - the requirement is "impossible", and the debug APK is what
        // goes on the phones. Consequence: emulator screenshots come out black,
        // which is expected, not a bug (see CLAUDE.md).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val container = (application as TrombadarioApp).container

        // Coming back from the background is the moment the phone is most likely
        // to have changed networks since the last check.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.homeNetworkGate.refresh()
            }
        }

        setContent {
            val theme by container.serverConfigStore.theme
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)

            TrombadarioTheme(preference = theme) {
                CompositionLocalProvider(
                    LocalWindowWidthSizeClass provides calculateWindowSizeClass(this).widthSizeClass
                ) {
                    // A folha fica aqui, na raiz: desenhada uma vez, aparece
                    // em toda tela sem cada uma ter que se lembrar dela.
                    // Dentro do MainNavHost cada tela desenha a sua por cima
                    // desta, para poder virar; esta é a página de baixo, a que
                    // aparece enquanto a de cima está levantada.
                    DeskBackground {
                        NotebookBackground {
                            AppNavigation(container)
                        }
                    }
                }
            }
        }
    }
}
