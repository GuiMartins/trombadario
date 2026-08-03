package com.trombadario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `mantem url completa`() {
        assertEquals(
            "http://192.168.31.172:8090",
            ServerUrl.normalize("http://192.168.31.172:8090"),
        )
    }

    @Test
    fun `adiciona http quando falta o esquema`() {
        // É assim que o endereço fica anotado no servidor - digitar o "http://"
        // à mão é o tipo de coisa que o usuário esquece.
        assertEquals("http://192.168.31.172:8090", ServerUrl.normalize("192.168.31.172:8090"))
    }

    @Test
    fun `nao rebaixa https pra http`() {
        assertEquals("https://casa.exemplo", ServerUrl.normalize("https://casa.exemplo"))
    }

    @Test
    fun `tira barra e espaco das pontas`() {
        assertEquals(
            "http://192.168.31.172:8090",
            ServerUrl.normalize("  http://192.168.31.172:8090/  "),
        )
    }

    @Test
    fun `rejeita vazio`() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
    }

    @Test
    fun `rejeita url sem host`() {
        // OkHttp aceita parsear isso, mas com host vazio - viraria uma
        // requisição que falha muito depois, com erro bem pior de entender.
        assertNull(ServerUrl.normalize("http://"))
    }

    @Test
    fun `rejeita esquema que nao e http`() {
        assertNull(ServerUrl.normalize("ftp://192.168.31.172"))
    }
}
