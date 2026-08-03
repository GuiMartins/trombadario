package com.trombadario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val LightColors = lightColorScheme(
    primary = MarkerInk,
    onPrimary = OnMarkerRed,
    primaryContainer = NoteLight,
    onPrimaryContainer = InkLight,
    secondary = AccentTeal,
    onSecondary = OnMarkerRed,
    // Chip selecionado e indicador da nav bar saem daqui. Sem definir, o
    // Material entrega o lilás padrão dele - que destoa de tudo.
    secondaryContainer = NoteLight,
    onSecondaryContainer = InkLight,
    tertiary = AccentAmber,
    // Faltava - ver o comentário de AccentAmber em Color.kt. Sem isto o
    // Material usava o branco padrão dele, ilegível sobre o âmbar.
    onTertiary = InkLight,
    background = PaperLight,
    onBackground = InkLight,
    // Card branco sobre a folha - é o "papelzinho colado no caderno".
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = NoteLight,
    onSurfaceVariant = InkSoftLight,
    // outlineVariant é a linha pautada; NotebookBackground lê daqui.
    outlineVariant = PaperRuleLight,
    outline = NoteBorderLight,
    error = ErrorLight,
)

internal val DarkColors = darkColorScheme(
    primary = MarkerRedDark,
    onPrimary = OnMarkerRedDark,
    primaryContainer = NoteDark,
    onPrimaryContainer = InkDark,
    secondary = AccentTealDark,
    onSecondary = OnMarkerRedDark,
    secondaryContainer = NoteDark,
    onSecondaryContainer = InkDark,
    tertiary = AccentAmber,
    // Mesmo valor do claro: é o próprio tom do âmbar que não muda entre os
    // temas, não o texto (ver Color.kt).
    onTertiary = InkLight,
    background = PaperDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = NoteDark,
    onSurfaceVariant = InkSoftDark,
    outlineVariant = PaperRuleDark,
    outline = NoteBorderDark,
    error = ErrorDark,
)

/** Cantos generosos e botões em pílula, como no mockup. */
private val NotebookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun TrombadarioTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TrombadarioTypography,
        shapes = NotebookShapes,
        content = content,
    )
}
