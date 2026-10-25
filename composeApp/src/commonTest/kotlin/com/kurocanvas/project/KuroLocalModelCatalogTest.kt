package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KuroLocalModelCatalogTest {
    @Test
    fun defaultModelIsPinnedAndLicenseIsExplicit() {
        val model = KuroModelCatalog.defaultModel

        assertEquals("qwen3-0.6b-litertlm", model.id)
        assertEquals("Qwen3-0.6B.litertlm", model.fileName)
        assertEquals(613722264L, model.expectedSizeBytes)
        assertTrue(model.sha256.matches(Regex("[a-f0-9]{64}")))
        assertEquals("Apache-2.0", model.licenseId)
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertEquals("0.18.0", model.runtimeVersion)
    }

    @Test
    fun promptBuilderKeepsNativeCreativeToolsAndCanonBoundariesExplicit() {
        val system = KuroPromptBuilder.systemInstruction("Mira / hair color: Black")

        assertTrue(system.contains("native creative workspace"))
        assertTrue(system.contains("Never fill gaps with invented facts"))
        assertTrue(system.contains("Mira / hair color: Black"))
        assertTrue(system.contains("do not pretend to execute tools"))
    }

    @Test
    fun nativeActionParserAcceptsOnlyTheExplicitPanelProposalSchema() {
        val parsed = KuroNativeActionParser.parse(
            """{"kuro_action":{"type":"add_storyboard_panel","summary":"Add the next panel"}}"""
        )
        assertEquals(KuroNativeActionType.ADD_STORYBOARD_PANEL, parsed?.type)
        assertEquals("Add the next panel", parsed?.summary)

        val fenced = KuroNativeActionParser.parse(
            """```json
{"kuro_action":{"type":"add_storyboard_panel","summary":"Create panel 2"}}
```"""
        )
        assertEquals("Create panel 2", fenced?.summary)
    }

    @Test
    fun nativeActionParserRejectsUnsupportedOrAmbiguousPayloads() {
        assertEquals(
            null,
            KuroNativeActionParser.parse(
                """{"kuro_action":{"type":"delete_project","summary":"Delete it"}}"""
            )
        )
        assertEquals(
            null,
            KuroNativeActionParser.parse(
                """{"kuro_action":{"type":"add_storyboard_panel","summary":"Add it","projectId":"other"}}"""
            )
        )
        assertEquals(null, KuroNativeActionParser.parse("I added a panel for you."))
        assertEquals(
            null,
            KuroNativeActionParser.parse(
                """{"kuro_action":{"type":"add_storyboard_panel","summary":"   "}}"""
            )
        )
    }

    @Test
    fun progressPercentageIsBoundedAndHandlesUnknownTotal() {
        assertEquals(50, KuroModelDownloadProgress(50, 100).percent)
        assertEquals(0, KuroModelDownloadProgress(10, 0).percent)
        assertEquals(100, KuroModelDownloadProgress(200, 100).percent)
        assertFalse(KuroModelDownloadProgress(1, 3).percent < 0)
    }
}
