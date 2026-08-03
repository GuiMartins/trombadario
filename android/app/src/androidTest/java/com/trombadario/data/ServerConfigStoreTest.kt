package com.trombadario.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trombadario.ui.theme.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Precisa de device: DataStore grava em disco através do Context. */
@RunWith(AndroidJUnit4::class)
class ServerConfigStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ServerConfigStore(context)

    @Before
    fun limpaEstadoAnterior() = runTest {
        store.clearBaseUrl()
        store.setTheme(ThemePreference.SYSTEM)
    }

    @Test
    fun sem_servidor_configurado_o_endereco_e_nulo() = runTest {
        assertNull(store.baseUrl.first())
    }

    @Test
    fun guarda_o_endereco_do_servidor() = runTest {
        store.setBaseUrl("http://192.168.31.172:8090")

        assertEquals("http://192.168.31.172:8090", store.baseUrl.first())
    }

    @Test
    fun endereco_sobrevive_a_recriacao_do_store() = runTest {
        store.setBaseUrl("http://192.168.31.172:8090")

        // Simula o app sendo aberto de novo: instância nova, mesmo disco.
        assertEquals("http://192.168.31.172:8090", ServerConfigStore(context).baseUrl.first())
    }

    @Test
    fun trocar_de_servidor_apaga_o_endereco() = runTest {
        store.setBaseUrl("http://192.168.31.172:8090")

        store.clearBaseUrl()

        // Sem endereço o gate cai em Unpaired e a tela de configuração assume.
        assertNull(store.baseUrl.first())
    }

    @Test
    fun trocar_de_endereco_sobrescreve_o_anterior() = runTest {
        store.setBaseUrl("http://192.168.31.172:8090")

        store.setBaseUrl("http://192.168.31.200:8090")

        assertEquals("http://192.168.31.200:8090", store.baseUrl.first())
    }

    @Test
    fun tema_default_e_do_sistema_e_persiste_quando_trocado() = runTest {
        assertEquals(ThemePreference.SYSTEM, store.theme.first())

        store.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, ServerConfigStore(context).theme.first())
    }
}
