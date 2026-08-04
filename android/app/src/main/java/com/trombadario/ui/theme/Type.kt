package com.trombadario.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.trombadario.R

/** Letra de mão, só pra título e cabeçalho. */
val Handwriting = FontFamily(Font(R.font.caveat_brush))

/** Sans arredondada pro resto. Caderno é a moldura, não a legibilidade: texto
 *  corrido em letra de mão cansa e o filho é quem mais lê aqui. */
val Rounded = FontFamily(Font(R.font.nunito))

// Caveat Brush tem caixa-alta baixa e desenho apertado; nos tamanhos de título
// ela pede uns pontos a mais que a sans pra pesar igual na tela.
//
// Body/title/label ganharam +1/+2sp sobre o padrão do Material em 03/08/2026:
// os títulos já tinham sido aumentados de propósito (ver acima), mas o resto
// do texto - a maior parte do que se lê na tela - tinha ficado exatamente no
// tamanho padrão, e "tudo muito pequeno" foi a reclamação. Título continua
// intocado.
val TrombadarioTypography = Typography(
    displayLarge = TextStyle(fontFamily = Handwriting, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Handwriting, fontSize = 44.sp, lineHeight = 50.sp),
    headlineLarge = TextStyle(fontFamily = Handwriting, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = Handwriting, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = Handwriting, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Handwriting, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(
        fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(fontFamily = Rounded, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontSize = 14.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp
    ),
)
