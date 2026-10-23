package com.sneakyvenustrap.voice

import com.sneakyvenustrap.SneakyVenusTrap
import de.maxhenkel.voicechat.api.*
import de.maxhenkel.voicechat.api.events.*
import de.maxhenkel.voicechat.api.packets.MicrophonePacket
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.lang.reflect.Proxy
import java.util.function.Consumer
import java.util.concurrent.Executors

class VoiceIntegrationTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun registeredMicrophoneCallbackFeedsActualEncounterPredicateAndDisableMakesItInert() {
        lateinit var callback: Consumer<MicrophonePacketEvent>
        val registration = proxy<EventRegistration> { method, args ->
            if (method == "registerEvent") {
                assertEquals(MicrophonePacketEvent::class.java, args[0])
                @Suppress("UNCHECKED_CAST")
                callback = args[1] as Consumer<MicrophonePacketEvent>
            }
            null
        }
        val provider = MockBukkit.createMockPlugin()
        server.servicesManager.register(BukkitVoicechatService::class.java, BukkitVoicechatService {
            assertEquals("sneakyvenustrap", it.pluginId)
            it.registerEvents(registration)
        }, provider, ServicePriority.Normal)
        val speaker = server.addPlayer()
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val voicePlayer = proxy<ServerPlayer> { method, _ ->
            if (method == "getUuid") speaker.uniqueId else error("No Bukkit/position access permitted: $method")
        }
        val connection = proxy<VoicechatConnection> { method, _ ->
            when (method) { "getPlayer" -> voicePlayer; "isInGroup", "isDisabled" -> false; else -> error(method) }
        }
        var bytes = byteArrayOf(1)
        val packet = proxy<MicrophonePacket> { method, _ -> if (method == "getOpusEncodedData") bytes else error(method) }
        val event = proxy<MicrophonePacketEvent> { method, _ ->
            when (method) { "getSenderConnection" -> connection; "getPacket" -> packet; "isCancelled" -> false; else -> error(method) }
        }
        assertFalse(plugin.encounters.microphoneActive(speaker.uniqueId))
        val executor = Executors.newSingleThreadExecutor()
        try {
            bytes = byteArrayOf()
            executor.submit { callback.accept(event) }.get()
            assertFalse(plugin.encounters.microphoneActive(speaker.uniqueId), "end-of-stream is not speech")
            bytes = byteArrayOf(1)
            executor.submit { callback.accept(event) }.get()
            assertTrue(plugin.encounters.microphoneActive(speaker.uniqueId), "bootstrap must wire genuine callback to hunting")
            server.pluginManager.disablePlugin(plugin)
            executor.submit { callback.accept(event) }.get()
            assertFalse(plugin.encounters.microphoneActive(speaker.uniqueId))
        } finally { executor.shutdownNow() }
    }

    @Test fun absentServiceLeavesHuntingAvailableWithoutMicrophoneActivity() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        assertFalse(plugin.encounters.microphoneActive(server.addPlayer().uniqueId))
    }

    private inline fun <reified T> proxy(crossinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        } as T
}
