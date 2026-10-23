package com.emptyplugin

import org.mockbukkit.mockbukkit.MockBukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmptyPluginTest {

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun loadsAndEnablesWithoutError() {
        val plugin = MockBukkit.load(EmptyPlugin::class.java)
        assertNotNull(plugin)
        assertTrue(plugin.isEnabled)
        assertNotNull(EmptyPlugin.instance)
    }
}
