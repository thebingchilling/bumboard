package app.lychee.chinese

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * Traditional or Simplified, chosen twice: once for the whole of Lychee (menus, names and
 * labels with Chinese in them: 粵語 / 粤语 on the space bar, the settings), and once for the
 * candidates (the 繁/简 key), which follows the first until it is set.
 */
object ChineseScript {
    /** Lychee's own script: "traditional" or "simplified". */
    const val PREF_UI = "lychee_chinese_script_ui"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Suppress("DEPRECATION")
    private fun prefs(context: Context?): SharedPreferences? =
        prefs ?: context?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext).also { p -> prefs = p } }

    @JvmStatic
    fun init(context: Context) {
        prefs(context)
    }

    /** Whether Lychee's menus and labels show Simplified characters. */
    @JvmStatic
    fun uiSimplified(prefs: SharedPreferences): Boolean = prefs.getString(PREF_UI, "traditional") == "simplified"

    /** Whether the candidates are Simplified (their own choice, or Lychee's until there is one). */
    @JvmStatic
    fun candidatesSimplified(prefs: SharedPreferences): Boolean =
        (prefs.getString(ChineseInput.PREF_SCRIPT, null) ?: prefs.getString(PREF_UI, "traditional")) == "simplified"

    /** [text] (a menu item, name or label) in Lychee's script. */
    @JvmStatic
    @JvmOverloads
    fun ui(text: String, context: Context? = null): String {
        val p = prefs(context) ?: return text
        return if (uiSimplified(p)) simplify(text) else text
    }

    /** The Traditional characters Lychee's own labels use, in Simplified. */
    fun simplify(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(SIMPLIFIED[ch] ?: ch)
        return sb.toString()
    }

    private val SIMPLIFIED: Map<Char, Char> = run {
        val trad = "粵語話寫這裡輸書進階漢們國對個會學時說點開關長門間見現發經過還來為與筆劃讀聽詞義釋類標體範廣東麼嗎號碼盤鍵選擇單雙種風樣設應這語輕顯示憶錄"
        val simp = "粤语话写这里输书进阶汉们国对个会学时说点开关长门间见现发经过还来为与笔划读听词义释类标体范广东么吗号码盘键选择单双种风样设应这语轻显示忆录"
        trad.indices.associate { trad[it] to simp[it] }
    }
}
