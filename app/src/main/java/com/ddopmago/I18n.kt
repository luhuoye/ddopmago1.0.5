package com.ddopmago

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** 界面语言：yi 凉山彝文（默认）/ zh 简体中文 / en English。
 *  彝文词的来源优先级：典籍与权威机构（规范彝文词典）→ 正规机构（民族出版社《彝汉英常用词词汇》）→ 网上权威（无）→ 都没有则显示简体中文 */
object I18n {
    var lang by mutableStateOf("yi")
    var chosen by mutableStateOf(false)
    var pyMode by androidx.compose.runtime.mutableIntStateOf(0) // 0凉山拼音 1国际拼音(IPA)
    private var en = HashMap<String, String>()
    private var yi = HashMap<String, String>()
    private var registry = HashSet<String>()
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("yi_lang", Context.MODE_PRIVATE)
        lang = sp.getString("lang", "yi") ?: "yi"
        chosen = sp.getBoolean("chosen", false)
        pyMode = sp.getInt("pym", 0)
        en = load(ctx, "ui_en.json")
        yi = load(ctx, "ui_yi.json")
        try {
            val a = org.json.JSONArray(ctx.assets.open("ui_all.json").bufferedReader().use { it.readText() })
            for (i in 0 until a.length()) registry.add(a.getString(i))
        } catch (_: Throwable) {}
    }

    private fun load(ctx: Context, name: String): HashMap<String, String> {
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(ctx.assets.open(name).bufferedReader().use { it.readText() })
            o.keys().forEach { m[it] = o.getString(it) }
        } catch (_: Throwable) {}
        return m
    }

    fun updatePyMode(m: Int) {
        pyMode = m
        sp.edit().putInt("pym", m).apply()
    }

    fun set(l: String) {
        lang = l; chosen = true
        sp.edit().putString("lang", l).putBoolean("chosen", true).apply()
    }

    const val PH = "ꀋꐥ" // 暂时没有彝文译文时的占位

    /** 界面字符串：zh 原样；en 查英文表；yi 查彝文表，没有译文的界面文字显示 ꀋꐥ，不是界面文字（内容）原样显示 */
    fun t(s: String): String {
        if (lang == "zh" || s.isEmpty() || s.length > 140) return s
        val k = if (s in registry || s in en || s in yi) s else s.trim()
        if (lang == "en") {
            en[k]?.let { return it }
            val n = norm(s)
            if (n != null && n.first in en) return f(n.first, *n.second.toTypedArray())
            return s
        }
        yi[k]?.let { if (it.isNotEmpty()) return it }
        if (k in registry) return PH
        // 含书名、章名、数字的动态题干：把它们换成 {0}{1}… 再按模板翻译
        val n = norm(s)
        if (n != null && (n.first in registry || n.first in yi)) return f(n.first, *n.second.toTypedArray())
        return s
    }

    private val tok = Regex("《([^》]*)》|「([^」]*)」|\\d+|[\uA000-\uA48C]+")
    private fun norm(s: String): Pair<String, List<String>>? {
        val args = ArrayList<String>()
        val tpl = tok.replace(s) { m ->
            val i = args.size
            when {
                m.value.startsWith("《") -> { args.add(m.groupValues[1]); "《{" + i + "}》" }
                m.value.startsWith("「") -> { args.add(m.groupValues[2]); "「{" + i + "}」" }
                else -> { args.add(m.value); "{" + i + "}" }
            }
        }
        return if (args.isEmpty()) null else Pair(tpl, args)
    }

    /** 带参数的界面字符串，模板里用 {0} {1}…；参数里的界面词也会被翻译 */
    fun f(tpl: String, vararg a: Any?): String {
        val args = a.map { if (it is String) t(it) else it.toString() }
        // 模板里除占位符外没有汉字（如 "{0} · {1}"）：与语言无关，直接代入
        val neutral = tpl.replace(Regex("\\{\\d+\\}"), "").none { it.code in 0x4E00..0x9FFF }
        var r = if (neutral) tpl else when (lang) { "en" -> en[tpl] ?: tpl; "yi" -> yi[tpl]?.takeIf { it.isNotEmpty() } ?: PH; else -> tpl }
        args.forEachIndexed { i, x -> r = r.replace("{" + i + "}", x) }
        return r
    }
}

/** 全应用的 Text：自动按界面语言翻译（同包函数优先于 material3.Text）。翻成彝文时逐字标拼音；没有译文则显示简体中文 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val tt = I18n.t(text)
    if (fontFamily == null && Repo.hasYi(tt) && (tt != text || (I18n.lang == "yi" && tt.none { it.code in 0x4E00..0x9FFF }))) {
        Box(modifier) { YiText(tt, if (fontSize == TextUnit.Unspecified) 16.sp else fontSize, color) }
    } else {
        androidx.compose.material3.Text(
            text = tt, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = onTextLayout ?: {}
        )
    }
}
