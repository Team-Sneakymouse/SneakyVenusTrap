package com.emptyplugin.util

import com.sneakyutils.util.TextUtility
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Smoke tests against SneakyUtils [TextUtility] (not owned by this template).
 * Keeps a working JUnit example for pure helpers without shipping a local copy.
 */
class TextUtilityTest {

    @Test
    fun replaceFormatCodes_convertsLegacyAmpersandColors() {
        val converted = TextUtility.replaceFormatCodes("&cHello")
        assertEquals("<red>Hello", converted)
    }

    @Test
    fun replaceFormatCodes_convertsSectionSignToAmpersand() {
        val converted = TextUtility.replaceFormatCodes("\u00A7aOK")
        assertEquals("<green>OK", converted)
    }

    @Test
    fun convertToComponent_stripsItalicByDefault() {
        val plain =
            PlainTextComponentSerializer.plainText()
                .serialize(TextUtility.convertToComponent("&7plain"))
        assertEquals("plain", plain)
    }

    @Test
    fun splitIntoLines_preservesWordsWhenWrapping() {
        val input = "one two three four five six"
        val lines = TextUtility.splitIntoLines(input, maxLineLength = 10)
        assertEquals(input, lines.joinToString(" "))
    }

    @Test
    fun splitIntoLines_keepsShortTextOnOneLine() {
        assertEquals(listOf("hello"), TextUtility.splitIntoLines("hello", maxLineLength = 80))
    }
}
