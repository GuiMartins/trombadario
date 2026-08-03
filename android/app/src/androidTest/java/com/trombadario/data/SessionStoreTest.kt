package com.trombadario.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Precisa de device: EncryptedSharedPreferences depende do keystore do Android.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun limpaEstadoAnterior() {
        SessionStore(context).clear()
    }

    @Test
    fun guarda_e_devolve_o_token() {
        val store = SessionStore(context)

        store.save("token-abc")

        assertEquals("token-abc", store.token.value)
    }

    @Test
    fun token_sobrevive_a_recriacao_do_store() {
        SessionStore(context).save("token-abc")

        // Simula o app sendo aberto de novo: instância nova, mesmo disco.
        assertEquals("token-abc", SessionStore(context).token.value)
    }

    @Test
    fun logout_apaga_o_token_do_disco() {
        val store = SessionStore(context)
        store.save("token-abc")

        store.clear()

        assertNull(store.token.value)
        assertNull(SessionStore(context).token.value)
    }

    @Test
    fun token_nao_fica_em_texto_puro_no_arquivo() {
        SessionStore(context).save("token-secreto-do-pai")

        val arquivo = context.filesDir.parentFile!!
            .resolve("shared_prefs/trombadario_session.xml")
        val conteudo = arquivo.readText()

        // O ponto de usar EncryptedSharedPreferences em vez de DataStore puro:
        // nem o valor nem o nome da chave aparecem legíveis.
        assertNotEquals(true, conteudo.contains("token-secreto-do-pai"))
        assertNotEquals(true, conteudo.contains("access_token"))
    }
}
