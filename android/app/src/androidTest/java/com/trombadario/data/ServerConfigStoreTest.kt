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
        store.unpair()
        store.setTheme(ThemePreference.SYSTEM)
    }

    @Test
    fun sem_pareamento_o_endereco_e_nulo() = runTest {
        assertNull(store.baseUrl.first())
        assertNull(store.pairedServerId.first())
    }

    @Test
    fun parear_guarda_endereco_e_server_id() = runTest {
        store.pair("http://192.168.31.172:8090", "uuid-do-servidor")

        assertEquals("http://192.168.31.172:8090", store.baseUrl.first())
        assertEquals("uuid-do-servidor", store.pairedServerId.first())
    }

    @Test
    fun despareaer_apaga_os_dois() = runTest {
        store.pair("http://192.168.31.172:8090", "uuid-do-servidor")

        store.unpair()

        // Os dois juntos: com só um deles o gate ficaria num estado ambíguo,
        // sem saber se deve pedir configuração ou bloquear.
        assertNull(store.baseUrl.first())
        assertNull(store.pairedServerId.first())
    }

    @Test
    fun tema_default_e_do_sistema_e_persiste_quando_trocado() = runTest {
        assertEquals(ThemePreference.SYSTEM, store.theme.first())

        store.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, ServerConfigStore(context).theme.first())
    }
}
