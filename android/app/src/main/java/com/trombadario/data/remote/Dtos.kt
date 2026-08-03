package com.trombadario.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val app: String,
    @SerialName("server_id") val serverId: String,
    // Default false so an older server (before the setup wizard existed) still
    // parses instead of failing the whole health check.
    @SerialName("setup_required") val setupRequired: Boolean = false,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_CHILD = "child"
    }
}

@Serializable
data class UserCreateDto(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
)

@Serializable
data class UserUpdateDto(
    @SerialName("display_name") val displayName: String? = null,
    val password: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class TrombadiceDto(
    val id: Int,
    val title: String,
    val description: String,
    // ISO-8601 with an explicit offset, always UTC - the backend guarantees it
    // (see backend/app/types.py). Parsed with Instant, rendered in local time.
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
    @SerialName("author_id") val authorId: Int,
    /** The task that wasn't done, when this trombadice is about one. */
    @SerialName("task_id") val taskId: Int? = null,
    /** Trombadice ou conquista; ver [Tipo]. Default para o app instalado hoje
     *  não quebrar se o servidor for mais novo que ele. */
    val kind: String = Tipo.TROMBADICE,
    /** Lista fechada; ver [Categoria]. Default para o app instalado hoje não
     *  quebrar se um dia o servidor devolver um valor que ele não conhece. */
    val category: String = Categoria.OUTRA,
    /** Quando o filho viu. Nulo = ainda não viu. Marcado sozinho pelo servidor
     *  na leitura dele - não existe chamada para o app fazer. */
    @SerialName("seen_at") val seenAt: String? = null,
)

/** O que o registro é: coisa errada ou coisa boa. */
object Tipo {
    const val TROMBADICE = "trombadice"
    const val CONQUISTA = "conquista"

    val TODOS = listOf(TROMBADICE, CONQUISTA)
}

/**
 * As mesmas categorias do backend, na mesma ordem - é a ordem da tela.
 *
 * **Cada uma pertence a um tipo.** "Falta de respeito" não descreve coisa boa e
 * "ajudou sem pedir" não descreve trombadice; oferecer as dezesseis nas duas
 * telas só produziria registro sem sentido. O servidor recusa a combinação
 * errada, então isto aqui é a mesma regra do lado de cá, não a única defesa.
 */
object Categoria {
    const val DESRESPEITO = "desrespeito"
    const val EDUCACAO = "educacao"
    const val NAO_FEZ = "nao_fez"
    const val MENTIRA = "mentira"
    const val BIRRA = "birra"
    const val ESCOLA = "escola"
    const val AGRESSAO = "agressao"
    const val OUTRA = "outra"

    const val AJUDOU = "ajudou"
    const val RESPONSABILIDADE = "responsabilidade"
    const val ESTUDOU = "estudou"
    const val GENTILEZA = "gentileza"
    const val INICIATIVA = "iniciativa"
    const val SUPEROU = "superou"
    const val CUIDOU = "cuidou"
    const val OUTRA_BOA = "outra_boa"

    val DE_TROMBADICE = listOf(
        DESRESPEITO, EDUCACAO, NAO_FEZ, MENTIRA, BIRRA, ESCOLA, AGRESSAO, OUTRA,
    )
    val DE_CONQUISTA = listOf(
        AJUDOU, RESPONSABILIDADE, ESTUDOU, GENTILEZA, INICIATIVA, SUPEROU, CUIDOU, OUTRA_BOA,
    )
    val TODAS = DE_TROMBADICE + DE_CONQUISTA

    fun de(tipo: String): List<String> =
        if (tipo == Tipo.CONQUISTA) DE_CONQUISTA else DE_TROMBADICE

    fun padraoDe(tipo: String): String = if (tipo == Tipo.CONQUISTA) OUTRA_BOA else OUTRA
}

@Serializable
data class TrombadiceCreateDto(
    val title: String,
    val description: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
    @SerialName("task_id") val taskId: Int? = null,
    val kind: String = Tipo.TROMBADICE,
    val category: String = Categoria.OUTRA,
)

@Serializable
data class TrombadiceUpdateDto(
    val title: String? = null,
    val description: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("task_id") val taskId: Int? = null,
    val category: String? = null,
)

@Serializable
data class ApiErrorDto(val detail: String? = null)

// --------------------------------------------------------------------------
// Tarefas
// --------------------------------------------------------------------------

@Serializable
data class TaskDto(
    val id: Int,
    val name: String,
    val description: String,
    val periodicity: String,
    /** Python weekday numbers, 0 = Monday. */
    val weekdays: List<Int> = emptyList(),
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("child_id") val childId: Int,
    @SerialName("is_active") val isActive: Boolean,
) {
    companion object {
        const val DAILY = "daily"
        const val WEEKLY = "weekly"
        const val MONTHLY = "monthly"
        const val ONCE = "once"
    }
}

@Serializable
data class TaskCreateDto(
    val name: String,
    val description: String = "",
    val periodicity: String = TaskDto.DAILY,
    val weekdays: List<Int> = emptyList(),
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("child_id") val childId: Int,
)

@Serializable
data class TaskUpdateDto(
    val name: String? = null,
    val description: String? = null,
    val periodicity: String? = null,
    val weekdays: List<Int>? = null,
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

// --------------------------------------------------------------------------
// Castigos
// --------------------------------------------------------------------------

@Serializable
data class PunishmentDto(
    val id: Int,
    val reason: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("ended_early_at") val endedEarlyAt: String? = null,
    /** Quando o filho viu este castigo. Nulo = ainda não viu. */
    @SerialName("seen_at") val seenAt: String? = null,
    @SerialName("child_id") val childId: Int,
    @SerialName("trombadice_ids") val trombadiceIds: List<Int> = emptyList(),
    // As trombadices completas - o filho não precisa de uma segunda busca só
    // pra saber o título de cada uma, e o pai já as tinha de graça.
    val trombadices: List<TrombadiceDto> = emptyList(),
    // Computed server-side: the phone's clock is not the authority on whether
    // someone is grounded.
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class PunishmentCreateDto(
    @SerialName("child_id") val childId: Int,
    @SerialName("ends_at") val endsAt: String,
    val reason: String = "",
    @SerialName("trombadice_ids") val trombadiceIds: List<Int> = emptyList(),
)

@Serializable
data class PunishmentUpdateDto(
    val reason: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("trombadice_ids") val trombadiceIds: List<Int>? = null,
    @SerialName("end_now") val endNow: Boolean? = null,
)

// --------------------------------------------------------------------------
// Frases de abertura
// --------------------------------------------------------------------------

@Serializable
data class SplashMessageDto(
    val id: Int,
    val text: String,
    /** null = vale pra todos os filhos. */
    @SerialName("child_id") val childId: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class SplashMessageCreateDto(
    val text: String,
    @SerialName("child_id") val childId: Int? = null,
)

@Serializable
data class SplashMessageUpdateDto(
    val text: String? = null,
    @SerialName("child_id") val childId: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

/** `text` nulo quando não há frase que se aplique - o app pula a tela. */
@Serializable
data class SplashMessageRandomDto(val text: String? = null)


// --------------------------------------------------------------------------
// Relatório
// --------------------------------------------------------------------------

@Serializable
data class ContagemDto(val rotulo: String, val total: Int)

/** Espelha `schemas.Report`. Os nomes vêm em português porque o backend também
 *  fala português aqui - inventar uma tradução no meio só criaria dois
 *  vocabulários para a mesma coisa. */
@Serializable
data class ReportDto(
    val de: String,
    val ate: String,
    val total: Int,
    @SerialName("por_dia") val porDia: List<ContagemDto> = emptyList(),
    @SerialName("por_semana") val porSemana: List<ContagemDto> = emptyList(),
    @SerialName("por_mes") val porMes: List<ContagemDto> = emptyList(),
    @SerialName("por_categoria") val porCategoria: List<ContagemDto> = emptyList(),
    @SerialName("por_filho") val porFilho: List<ContagemDto> = emptyList(),
    @SerialName("por_tarefa") val porTarefa: List<ContagemDto> = emptyList(),
    @SerialName("dias_com_registro") val diasComRegistro: Int = 0,
    @SerialName("media_por_dia_com_registro") val mediaPorDiaComRegistro: Double = 0.0,
    @SerialName("dia_mais_pesado") val diaMaisPesado: ContagemDto? = null,
    @SerialName("castigos_no_periodo") val castigosNoPeriodo: Int = 0,
    @SerialName("dias_de_castigo") val diasDeCastigo: Double = 0.0,
    @SerialName("maior_sequencia_limpa") val maiorSequenciaLimpa: Int = 0,
    @SerialName("nao_vistas") val naoVistas: Int = 0,
    val conquistas: Int = 0,
    @SerialName("conquistas_por_categoria")
    val conquistasPorCategoria: List<ContagemDto> = emptyList(),
)

@Serializable
data class DatasComRegistroDto(val datas: List<String> = emptyList())
