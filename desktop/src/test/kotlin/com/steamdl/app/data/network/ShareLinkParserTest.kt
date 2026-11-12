package com.steamdl.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun parsesAllSupportedPlatforms() {
        val cases = listOf(
            "https://pan.quark.cn/s/Abc123?pwd=a1B2" to (SharePlatform.QUARK to "Abc123"),
            "https://pan.xunlei.com/s/Abc_123-xy" to (SharePlatform.XUNLEI to "Abc_123-xy"),
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz" to (SharePlatform.BAIDU to "Abc_123-xy"),
            "https://yun.139.com/shareweb/#/w/i/Abc_123" to (SharePlatform.C139 to "Abc_123"),
            "https://www.guangyapan.com/s/Abc_123-xy" to (SharePlatform.GUANGYA to "Abc_123-xy")
        )

        cases.forEach { (text, expected) ->
            val parsed = ShareLinkParser.parse(text)!!
            assertEquals(expected.first, parsed.platform)
            assertEquals(expected.second, parsed.shareId)
        }
    }

    @Test
    fun rejectsUnsupportedPlatforms() {
        // SteamDL 只开放站内使用的网盘（夸克/迅雷/百度/139/光鸭），其余平台一律不解析
        listOf(
            "https://drive.uc.cn/s/Abc123",
            "https://www.123pan.com/s/2785Vv-T4Ded",
            "https://115.com/s/swabcdefg123",
            "https://www.lanzoub.com/iAbc123",
            "https://ilanzou.com/s/Abc123"
        ).forEach { text ->
            assertNull(text, ShareLinkParser.parse(text))
        }
    }

    @Test
    fun explicitTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://pan.quark.cn/s/Abc123 提取码：a1B2")!!
        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun rejectsUnrelatedUrl() {
        assertNull(ShareLinkParser.parse("https://example.com/s/Abc123"))
    }
}
