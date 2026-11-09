package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ZhuyinTest {
    @Test
    fun syllables() {
        assertEquals("ㄓㄨㄥ ㄍㄨㄛˊ", Zhuyin.format("zhong1 guo2"))
        assertEquals("ㄋㄧˇ ㄏㄠˇ", Zhuyin.format("ni3 hao3"))
        assertEquals("ㄕˋ", Zhuyin.syllable("shi4"))
        assertEquals("ㄩˊ", Zhuyin.syllable("yu2"))
        assertEquals("ㄐㄩㄝˊ", Zhuyin.syllable("jue2"))
        assertEquals("ㄌㄩˋ", Zhuyin.syllable("lv4"))
        assertEquals("ㄌㄩˋ", Zhuyin.syllable("lu:4"))
        assertEquals("˙ㄇㄚ", Zhuyin.syllable("ma5"))
        assertEquals("ㄨㄟˋ", Zhuyin.syllable("wei4"))
        assertEquals("ㄒㄩㄥ", Zhuyin.syllable("xiong1"))
    }
}
