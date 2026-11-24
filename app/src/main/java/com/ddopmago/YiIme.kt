package com.ddopmago

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.ln

/** 凉山彝文输入法引擎（据《凉山彝文输入法 SPEC》）：
 *  音节 = 声母(43+零声母) + 韵母(10) + 声调(t/x/p/无)。支持全拼、简拼（首字母）、声母/韵母/声调的任意组合；
 *  撇号 ' 是隔字符：每个 ' 隔开一个彝文字的拼音（可只写首字母、声母、韵母、声调中的一个或几个），彝文输入时 ' 不输出；
 *  模糊音有代价表，按层级打分；词库 = 翻译板块的彝文词和句子（不含中文）+ 用户词频。 */
object YiIme {
    val INI = listOf("", "b", "p", "bb", "nb", "m", "hm", "f", "v", "d", "t", "dd", "nd", "n", "hn", "l", "hl", "g", "k", "gg", "mg", "ng",
        "h", "hx", "j", "q", "jj", "nj", "ny", "x", "z", "c", "zz", "nz", "s", "ss", "zh", "ch", "rr", "nr", "sh", "r", "y", "w")
    val FIN = listOf("i", "ie", "a", "uo", "o", "e", "u", "ur", "y", "yr")
    private val iniIdx = INI.withIndex().associate { it.value to it.index }
    private val finIdx = FIN.withIndex().associate { it.value to it.index }
    private val finBySuffix = FIN.sortedByDescending { it.length }

    class Entry(val w: String, val sy: IntArray, val freq: Int, val letters: String)
    class Cand(val text: String, val used: Int, val rest: String, val score: Double)
    class ImeResult(val segs: List<String>, val cands: List<Cand>)
    private class Pat(val ini: Int, val fin: Int, val tone: Int) // -1 = 未指定

    private val IC = Array(INI.size) { i -> IntArray(INI.size) { j -> if (i == j) 0 else 99 } }
    private val FC = Array(FIN.size) { i -> IntArray(FIN.size) { j -> if (i == j) 0 else 99 } }
    // 用户声调 u(1t 2x 3p) 对 字声调 c(0无 1t 2x 3p) 的代价
    private val TC = arrayOf(intArrayOf(0, 0, 0, 0), intArrayOf(20, 0, 18, 26), intArrayOf(4, 18, 0, 20), intArrayOf(22, 26, 20, 0))

    private fun pi(a: String, b: String, c: Int) { val x = iniIdx[a]; val y = iniIdx[b]; if (x != null && y != null) { IC[x][y] = c; IC[y][x] = c } }
    private fun pf(a: String, b: String, c: Int) { val x = finIdx[a]; val y = finIdx[b]; if (x != null && y != null) { FC[x][y] = c; FC[y][x] = c } }

    init {
        listOf(Triple("b", "p", 12), Triple("d", "t", 12), Triple("g", "k", 12), Triple("bb", "nb", 8), Triple("dd", "nd", 8), Triple("gg", "mg", 8),
            Triple("gg", "ng", 10), Triple("z", "zz", 12), Triple("zh", "rr", 12), Triple("j", "jj", 12), Triple("s", "ss", 10), Triple("sh", "r", 10),
            Triple("x", "y", 10), Triple("hx", "h", 10), Triple("hx", "f", 14), Triple("f", "p", 12), Triple("v", "w", 10), Triple("hm", "m", 12),
            Triple("hn", "n", 12), Triple("hl", "l", 12), Triple("c", "ch", 14), Triple("z", "zh", 16), Triple("s", "sh", 16), Triple("ny", "n", 14),
            Triple("nz", "z", 12), Triple("nj", "j", 12), Triple("nr", "rr", 12), Triple("q", "x", 14)).forEach { pi(it.first, it.second, it.third) }
        listOf(Triple("u", "ur", 12), Triple("y", "yr", 12), Triple("o", "uo", 8), Triple("e", "u", 10), Triple("i", "ie", 10), Triple("e", "ie", 12),
            Triple("a", "ie", 8), Triple("i", "e", 14), Triple("o", "e", 14)).forEach { pf(it.first, it.second, it.third) }
    }

    private var lex: List<Entry> = emptyList()
    private var byLen: Array<List<Entry>> = Array(0) { emptyList() }
    private val charPack = HashMap<Char, Int>()
    private var ready = false
    private var sp: SharedPreferences? = null
    private val user = HashMap<String, Int>()
    private var dirty = 0

    private fun splitIF(s: String): Pair<Int, Int>? {
        for (f in finBySuffix) if (s.endsWith(f)) {
            val ii = iniIdx[s.dropLast(f.length)]
            if (ii != null) return Pair(ii, finIdx[f]!!)
        }
        return null
    }

    private fun parseRom(r: String): Int? {
        var body = r; var t = 0
        if (r.length > 1 && r.last() in "txp" && splitIF(r.dropLast(1)) != null) { body = r.dropLast(1); t = "txp".indexOf(r.last()) + 1 }
        val sp2 = splitIF(body) ?: return null
        return sp2.first * 100 + sp2.second * 10 + t
    }

    /** 建立音节表和词库。Repo 必须已加载 */
    @Synchronized
    fun ensure(ctx: Context) {
        if (ready) return
        sp = ctx.applicationContext.getSharedPreferences("ime_user", Context.MODE_PRIVATE)
        try {
            val o = JSONObject(sp!!.getString("u", "{}") ?: "{}")
            o.keys().forEach { user[it] = o.getInt(it) }
        } catch (_: Throwable) {}
        for (c in 0xA000..0xA48C) { val ch = c.toChar(); Repo.rom(ch)?.let { r -> parseRom(r)?.let { charPack[ch] = it } } }
        val map = HashMap<String, Entry>()
        fun add(w: String, freq: Int) {
            if (w.length < 1 || w.length > 20) return
            val sy = IntArray(w.length)
            val sb = StringBuilder()
            for (i in w.indices) { sy[i] = charPack[w[i]] ?: return; sb.append(Repo.rom(w[i])) }
            val old = map[w]
            if (old == null || old.freq < freq) map[w] = Entry(w, sy, freq, sb.toString())
        }
        charPack.keys.forEach { add(it.toString(), 20) }                       // 1165 个字
        Repo.words.forEach { add(it, 10) }                                      // 彝语词典
        Repo.allSentsYi().forEach { s ->                                         // 句子和词典例词（只取彝文）
            s.first.split(Regex("[^\\uA000-\\uA48C]+")).filter { it.length >= 2 }.forEach { add(it, s.second) }
        }
        user.keys.forEach { add(it, 10) }
        lex = map.values.toList()
        byLen = Array(21) { n -> lex.filter { it.w.length == n } }
        ready = true
    }

    fun learn(word: String) {
        user[word] = (user[word] ?: 0) + 1
        if (++dirty >= 5) save()
    }

    fun save() {
        dirty = 0
        val o = JSONObject(); user.forEach { (k, v) -> o.put(k, v) }
        sp?.edit()?.putString("u", o.toString())?.apply()
    }

    private fun patsOf(seg: String): List<Pat> {
        val out = ArrayList<Pat>()
        val s = seg.lowercase()
        if (s.isEmpty()) return out
        if (s.length > 1 && s.last() in "txp") {
            val body = s.dropLast(1); val t = "txp".indexOf(s.last()) + 1
            splitIF(body)?.let { out.add(Pat(it.first, it.second, t)) }
            if (body.isNotEmpty()) iniIdx[body]?.let { out.add(Pat(it, -1, t)) }
            finIdx[body]?.let { out.add(Pat(-1, it, t)) }
        }
        splitIF(s)?.let { out.add(Pat(it.first, it.second, -1)) }
        if (s.isNotEmpty()) iniIdx[s]?.let { out.add(Pat(it, -1, -1)) }
        finIdx[s]?.let { out.add(Pat(-1, it, -1)) }
        return out.distinctBy { it.ini * 10000 + (it.fin + 1) * 10 + (it.tone + 1) }
    }

    private fun cost(p: Pat, s: Int): Int {
        var c = 0
        if (p.ini >= 0) { val x = IC[p.ini][s / 100]; if (x >= 99) return 99; c += x }
        if (p.fin >= 0) { val x = FC[p.fin][(s / 10) % 10]; if (x >= 99) return 99; c += x }
        if (p.tone > 0) c += TC[p.tone][s % 10]
        return c
    }

    private fun segmentations(letters: String): List<List<String>> {
        val res = ArrayList<List<String>>()
        fun rec(i: Int, cur: ArrayList<String>) {
            if (res.size >= 30) return
            if (i == letters.length) { res.add(ArrayList(cur)); return }
            for (len in minOf(6, letters.length - i) downTo 1) {
                val s = letters.substring(i, i + len)
                if (patsOf(s).isNotEmpty()) { cur.add(s); rec(i + len, cur); cur.removeAt(cur.size - 1) }
            }
        }
        rec(0, ArrayList())
        return res.sortedBy { it.size }.take(3)
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /** buf 里可以有撇号 '。返回切分和候选（整词优先，其次是前几个字，最后是相似拼写） */
    fun candidates(buf: String): ImeResult {
        val raw = buf.lowercase().filter { it in 'a'..'z' || it == '\'' }
        if (!ready || raw.isEmpty()) return ImeResult(emptyList(), emptyList())
        val sets: List<List<String>> = if ('\'' in raw) listOf(raw.split('\'').filter { it.isNotEmpty() }) else segmentations(raw)
        val best = HashMap<String, Cand>()
        for (S in sets) {
            if (S.isEmpty()) continue
            val pats = S.map { patsOf(it) }
            if (pats.any { it.isEmpty() }) continue
            for (j in minOf(S.size, 20) downTo 1) {
                val rest = S.drop(j).joinToString("'")
                for (e in byLen[j]) {
                    var tot = 0; var full = 0; var ok = true
                    for (k in 0 until j) {
                        var bc = 99; var bf = false
                        for (p in pats[k]) { val c = cost(p, e.sy[k]); if (c < bc || (c == bc && p.ini >= 0 && p.fin >= 0)) { bc = c; bf = p.ini >= 0 && p.fin >= 0 } }
                        if (bc > 26) { ok = false; break }
                        tot += bc; if (bf) full++
                    }
                    if (!ok || tot > 26 * minOf(j, 3)) continue
                    var sc = when {
                        tot == 0 && full == j -> 1000.0
                        tot == 0 -> 760.0 - 30 * (j - full)
                        tot <= 12 * j -> 700.0 - tot
                        else -> 420.0 - tot
                    }
                    sc -= 300.0 * (S.size - j)
                    sc += minOf(32.0, ln(e.freq + 1.0) * 6) + minOf(60.0, (user[e.w] ?: 0) * 12.0)
                    val o = best[e.w]
                    if (o == null || o.score < sc) best[e.w] = Cand(e.w, j, rest, sc)
                }
            }
        }
        var list = best.values.sortedByDescending { it.score }.take(60)
        if (list.size < 4 && raw.length >= 2) {   // 层级 5~7：拼写相似
            val q = raw.replace("'", "")
            val extra = ArrayList<Cand>()
            for (e in lex) {
                if (kotlin.math.abs(e.letters.length - q.length) > 3 || e.letters[0] != q[0]) continue
                val d = lev(q, e.letters); val sim = 1.0 - d.toDouble() / maxOf(q.length, e.letters.length)
                if (sim >= 0.6 && best[e.w] == null) extra.add(Cand(e.w, e.w.length.coerceAtMost(S0(sets)), "", 200 + 80 * sim))
            }
            list = (list + extra.sortedByDescending { it.score }.take(20))
        }
        return ImeResult(sets.firstOrNull() ?: emptyList(), list.distinctBy { it.text })
    }

    private fun S0(sets: List<List<String>>) = sets.firstOrNull()?.size ?: 1
}
