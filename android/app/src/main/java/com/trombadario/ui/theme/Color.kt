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

// AccentTeal pinta o papel `secondary` do Material (fundo com texto branco em
// cima). "São identidade, não superfície, ficam iguais nos dois temas" era a
// desculpa daqui antes - e é a mesma que quebrou o teal da web: o mesmo tom
// no claro e no escuro dava 2,98:1 contra o texto branco no claro (o mínimo é
// 4,5:1). Este, escurecido, dá 5,12:1 no claro; no escuro o tom original já
// passava contra o texto escuro usado lá (5,01:1), então só o claro precisou
// de um valor novo - ver ConquistaTeal/ConquistaTealDark pro mesmo padrão.
val AccentTeal = Color(0xFF1D7A77)
val AccentTealDark = Color(0xFF40A4A1)

// AccentAmber pinta `tertiary`. Ao contrário do teal, o tom em si não precisa
// mudar entre os temas - quem faltava fixar era o texto por cima: `onTertiary`
// nunca tinha sido definido em Theme.kt, então herdava o branco padrão do
// Material em vez de tinta escura (1,86:1, ilegível). Com `onTertiary = InkLight`
// nos dois esquemas (Theme.kt), este mesmo tom dá 5,79:1 - o mesmo raciocínio
// do `--on-amber` da web, onde o âmbar também não muda mas o texto sim.
val AccentAmber = Color(0xFFE0A030)

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

/**
 * As cores da festa de aniversário: confete, fogos e balões.
 *
 * Não entram no `ColorScheme` nem no `ContrastTest` porque **não carregam texto
 * nem delimitam campo** - são partículas desenhadas no `Canvas`, e é a regra
 * que o CSS da web já aplica a `--amber` e `--coral`. O que elas precisam é de
 * outra coisa: aparecer tanto sobre o papel creme quanto sobre o papel escuro,
 * então são todas de meio-tom - nada de amarelo-claro, que some no claro, nem
 * de vinho, que some no escuro.
 */
val FestaCores = listOf(
    Color(0xFFE4634F), // coral, o mesmo acento da paleta
    Color(0xFFE0A030), // âmbar
    Color(0xFF2E9E9B), // teal
    Color(0xFFD9548E), // rosa
    Color(0xFF7C5CC4), // roxo
    Color(0xFF4FA84F), // verde
)
