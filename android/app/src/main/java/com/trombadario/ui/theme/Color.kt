package com.trombadario.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta de caderno de papel, amostrada direto do mockup do usuário
// (pixels do PDF, não olhômetro).
val PaperLight = Color(0xFFFBF4E2)        // fundo da folha
val PaperRuleLight = Color(0xFFE6D9B8)    // linha pautada
val PaperMarginLight = Color(0xFFE7A79A)  // margem vermelha da lateral
val CardLight = Color(0xFFFFFDF7)         // card branco sobre a folha
val NoteLight = Color(0xFFFFF0C2)         // card de recado, amarelo
val NoteBorderLight = Color(0xFFFDE4AB)   // borda tracejada do recado
val InkLight = Color(0xFF3A2E22)          // texto principal, marrom escuro
// Texto secundário. O tom do mockup (#8A7357) reprovava em contraste nas três
// superfícies (4,10 no papel, 4,42 no card, 3,96 no recado - o mínimo é 4,5).
// Este dá 5,8 / 6,3 / 5,6, e continua claramente secundário contra os 12:1 do
// texto principal.
val InkSoftLight = Color(0xFF6F5C40)      // texto secundário
// A mesa sob a folha, só visível em tela larga. Tem que ficar atrás da folha,
// não competir com ela - por isso um tom mais fundo e menos saturado que o
// papel, e não a cor do recado.
val DeskLight = Color(0xFFE7D9BC)

// A caneta vermelha do professor. Duas intensidades, e o motivo é contraste
// medido, não gosto: o vermelho do mockup (MarkerRed) dá 3,3:1 sobre o papel -
// suficiente para texto grande, reprovado para texto pequeno e para branco por
// cima. MarkerInk é a mesma matiz mais funda, 4,9:1 no papel e 5,4:1 com branco.
// Assinatura da marca fica no claro; tudo que é lido de perto usa o fundo.
val MarkerRed = Color(0xFFE8562D)
val MarkerInk = Color(0xFFC13A14)
val OnMarkerRed = Color(0xFFFFFFFF)

// Cores dos avatares e etiquetas. Ficam iguais nos dois temas - são identidade,
// não superfície.
val AccentTeal = Color(0xFF40A4A1)
val AccentAmber = Color(0xFFE0A030)
val AccentCoral = Color(0xFFE4634F)

// Tema escuro: mesma metáfora, papel de caderno velho sob luz baixa. Não é o
// claro invertido - inverter um papel creme dá cinza sujo.
val PaperDark = Color(0xFF241E18)
val PaperRuleDark = Color(0xFF3A3026)
val PaperMarginDark = Color(0xFF7A4A42)
val CardDark = Color(0xFF2E2721)
val NoteDark = Color(0xFF3D3320)
val NoteBorderDark = Color(0xFF5C4A2A)
val InkDark = Color(0xFFF2E7D5)
val InkSoftDark = Color(0xFFB8A88E)
val DeskDark = Color(0xFF17120E)

// Sobre papel escuro o vermelho puro do marcador some; esta é a mesma matiz
// clareada pra manter contraste.
val MarkerRedDark = Color(0xFFFF8A5E)
val OnMarkerRedDark = Color(0xFF4A1600)

val ErrorLight = Color(0xFFB3261E)
val ErrorDark = Color(0xFFFFB4AB)

/**
 * A cor da conquista. É o teal que já existia na paleta do mockup, e serve
 * justamente porque não é a caneta vermelha: o pai bate o olho na lista e vê o
 * que é coisa boa sem precisar ler.
 */
val ConquistaTeal = Color(0xFF2E7D7B)   // 4,6:1 no papel claro
val ConquistaTealDark = Color(0xFF6FD3D0) // no papel escuro
