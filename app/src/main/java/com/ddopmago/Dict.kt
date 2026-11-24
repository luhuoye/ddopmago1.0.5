@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ddopmago

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File


@Composable
private fun SecTitle(s: String) {
    Text(s, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun SubChip(label: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(selected = on, onClick = onClick, label = { Text(label) })
}

/** 翻译 → 词：词典（彝汉词典：查询/词句分类；彝语词典）、检字（部首/拼音/音节表）、字体 */
@Composable
fun WordHub(st: Store) {
    val ctx = LocalContext.current
    var top by rememberSaveable { mutableIntStateOf(0) }
    var dsub by rememberSaveable { mutableIntStateOf(0) }
    var ysub by rememberSaveable { mutableIntStateOf(0) }
    var csub by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var sec by rememberSaveable { mutableStateOf<String?>(null) }
    var kb by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<MdictFile, Int>?>(null) }
    val useSearch = top == 0 || (top == 1 && csub >= 1)
    fun reset() { q = ""; sec = null; detail = null }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = top, containerColor = Color.White) {
            listOf("词典", "检字", "字体").forEachIndexed { i, n -> Tab(selected = top == i, onClick = { top = i; reset() }, text = { Text(n) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (top == 0) {
                SubChip("彝汉词典", dsub == 0) { dsub = 0; reset() }
                SubChip("彝语词典", dsub == 1) { dsub = 1; reset() }
                if (dsub == 0) {
                    SubChip("查询", ysub == 0) { ysub = 0; reset() }
                    SubChip("词句分类", ysub == 1) { ysub = 1; reset() }
                }
            } else if (top == 1) {
                SubChip("部首", csub == 0) { csub = 0; reset() }
                SubChip("拼音", csub == 1) { csub = 1; reset() }
                SubChip("音节表", csub == 2) { csub = 2; reset() }
            }
        }
        if (useSearch && detail == null) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                InputBox(q, when {
                    top == 0 && dsub == 0 && ysub == 0 -> "输入中文、彝文或拼音查词（点此打开键盘）"
                    top == 0 && dsub == 0 -> "搜索：中文 / 彝文（点此打开键盘）"
                    top == 0 -> "输入拼音或彝文检索"
                    csub == 1 -> "输入拼音查字，如 ne"
                    else -> "输入拼音筛选，如 ne"
                }, { q += it }) { kb = true }
            }
        }
        Box(Modifier.weight(1f)) {
            val d = detail
            when {
                d != null -> MdxDetail(d, { detail = it })
                top == 0 && dsub == 0 && ysub == 0 -> YiHanQuery(q, st) { detail = it }
                top == 0 && dsub == 0 -> ThemeCategories(q, st, sec) { sec = it }
                top == 0 -> WordList(q)
                top == 1 && csub == 0 -> RadicalHub(st)
                top == 1 && csub == 1 -> PinyinLookup(q, st)
                top == 1 -> SyllableTable(q)
                else -> FontsHub(st)
            }
        }
        if (useSearch && kb && detail == null) {
            AppKeyboard(
                if (top == 0 && dsub == 0) K_ZH else K_ABC, q,
                { q += it }, { q = q.dropLast(1) }, { kb = false }, { q += Clip.paste(ctx) }
            )
        }
    }
}

/** 彝汉词典·查询：同时查 规范彝文词典、2008词典、古彝文（释义） */
@Composable
fun YiHanQuery(q: String, st: Store, onOpen: (Pair<MdictFile, Int>) -> Unit) {
    val ctx = LocalContext.current
    remember { AncientData.ensure(ctx); true }
    var anc by remember { mutableStateOf<Anc?>(null) }
    val k = q.trim()
    val a = remember(k) { if (k.isEmpty()) emptyList() else Repo.searchDict(k).take(40) }
    val b = remember(k, Mdict.status) { if (k.isEmpty()) emptyList() else Mdict.dicts.flatMap { d -> d.search(k, 40).map { d to it } } }
    val c = remember(k) { AncientData.byMeaning(k).take(40) }
    var ch by remember { mutableStateOf<Char?>(null) }
    val d1165 = remember(k) {
        if (k.isEmpty()) emptyList()
        else Repo.chars.entries.filter { (_, v) -> v.mean.contains(k) || v.py.equals(k, true) || v.ipa.contains(k) }.map { it.key to it.value }.take(30)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (k.isEmpty()) item {
            Text("同时查询：规范彝文词典（例词）、2008词典（彝汉英常用词词汇）、古彝文（按释义）。", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(12.dp))
            if (Mdict.status != "ready") Text(if (Mdict.status == "installing") "2008词典正在安装，请稍候…" else "2008词典：" + Mdict.status, fontSize = 12.sp, color = Red, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (a.isNotEmpty()) { item { SecTitle(I18n.f("规范彝文词典（{0}）", a.size)) }; items(a) { SentCard(it, st) } }
        if (b.isNotEmpty()) {
            item { SecTitle(I18n.f("2008词典（{0}）", b.size)) }
            items(b) { (d, i) ->
                AppCard(Modifier.clickable { onOpen(d to i) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { MixText(d.keys[i], 22.sp, 17.sp) }
                        Text("›", color = Color.Gray)
                    }
                }
            }
        }
        if (d1165.isNotEmpty()) {
            item { SecTitle(I18n.f("彝文字（{0}）", d1165.size)) }
            items(d1165) { (yc, v) ->
                AppCard(Modifier.clickable { ch = yc }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        YiText(yc.toString(), 30.sp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(v.py + (if (v.ipa.isNotEmpty()) "   " + v.ipa else ""), fontSize = 13.sp, color = Red)
                            Text(v.mean, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        if (c.isNotEmpty()) { item { SecTitle(I18n.f("古彝文（{0}）", c.size)) }; items(c) { AncRow(it) { anc = it } } }
        if (k.isNotEmpty() && a.isEmpty() && b.isEmpty() && c.isEmpty() && d1165.isEmpty()) item { Text("没有找到相关内容", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
    }
    anc?.let { AncDetail(it, st) { anc = null } }
    ch?.let { YiCharDialog(it, null) { ch = null } }
}

/** 彝汉词典·词句分类 */
@Composable
fun ThemeCategories(q: String, st: Store, sec: String?, onSec: (String?) -> Unit) {
    val key = q.trim().lowercase()
    val list: List<Sent>? = when {
        key.isNotEmpty() -> Repo.sents.filter { it.zh.contains(key) || it.yi.contains(key) || Repo.pinyin(it.yi).replace(" ", "").contains(key.replace(" ", "")) }.take(60)
        sec != null -> Repo.sents.filter { it.sec == sec }
        else -> null
    }
    if (list == null) {
        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
            items(Repo.sections()) { s ->
                AppCard(Modifier.clickable { onSec(s) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(I18n.f("{0} 句  ›", Repo.sents.count { it.sec == s }), color = Color.Gray)
                    }
                }
            }
        }
    } else {
        Column {
            if (key.isEmpty()) TextButton(onClick = { onSec(null) }) { Text(I18n.f("‹ 返回分类（{0}）", sec)) }
            else Text(I18n.f("找到 {0} 条", list.size), color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.padding(horizontal = 8.dp)) { items(list) { SentCard(it, st) } }
        }
    }
}

/** 彝语词典（5.7 万个彝文词） */
@Composable
fun WordList(q: String) {
    val ctx = LocalContext.current
    val list = remember(q) { Repo.searchWords(q) }
    Column {
        Text(
            if (q.isBlank()) I18n.f("词库共 {0} 个彝文词，输入拼音（如 ddop）或彝文查找", Repo.words.size) else I18n.f("找到 {0} 个（最多显示 80）", list.size),
            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
            items(list) { w ->
                AppCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            YiText(w, 24.sp)
                            Repo.gloss[w]?.let { Text(it, fontSize = 16.sp) }
                        }
                        TextButton(onClick = { Clip.copy(ctx, w) }) { Text("复制") }
                    }
                }
            }
        }
    }
}

/** 检字·拼音：输入拼音，查现代彝文字（按音节）和古彝文（按方言读音） */
@Composable
fun PinyinLookup(q: String, st: Store) {
    val ctx = LocalContext.current
    remember { AncientData.ensure(ctx); true }
    var anc by remember { mutableStateOf<Anc?>(null) }
    var ch by remember { mutableStateOf<Char?>(null) }
    val key = q.trim().lowercase().filter { it in 'a'..'z' }
    val mod = remember(key) { if (key.isEmpty()) emptyList() else Repo.syllables().filter { it.first.startsWith(key) }.take(150) }
    val ancs = remember(key) { AncientData.byPron(key).take(60) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (key.isEmpty()) item { Text("输入拼音（如 ne、ddop）：列出读这个音的现代彝文字，以及方言读音含该拼音的古彝字", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(12.dp)) }
        if (mod.isNotEmpty()) {
            item {
                SecTitle(I18n.f("现代彝文（{0}）", mod.size))
                FlowRow {
                    mod.forEach { (r, c) ->
                        Column(Modifier.padding(3.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { ch = c }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c.toString(), fontSize = 28.sp, fontFamily = Fonts.family)
                            Text(r, fontSize = 10.sp, color = PyColor)
                        }
                    }
                }
            }
        }
        if (ancs.isNotEmpty()) { item { SecTitle(I18n.f("古彝文（{0}）", ancs.size)) }; items(ancs) { AncRow(it) { anc = it } } }
        if (key.isNotEmpty() && mod.isEmpty() && ancs.isEmpty()) item { Text("没有找到相关内容", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
    }
    anc?.let { AncDetail(it, st) { anc = null } }
    ch?.let { c -> YiCharDialog(c, null) { ch = null } }
}

/** 检字·音节表（全部 1165 个字） */
@Composable
fun SyllableTable(q: String) {
    val ctx = LocalContext.current
    val key = q.trim().lowercase()
    val list = remember(q) { Repo.syllables().filter { key.isEmpty() || it.first.startsWith(key) } }
    Column {
        Text(I18n.f("共 {0} 个音节（全部 1165 个规范彝文字），点击可复制", list.size), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(horizontal = 8.dp)) {
            items(list) { (r, c) ->
                Card(
                    Modifier.padding(4.dp).clickable { Clip.copy(ctx, c.toString()) }, shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.toString(), fontSize = 30.sp, fontFamily = Fonts.family)
                        Text(r, fontSize = 13.sp, color = Red)
                        Repo.chars[c]?.ipa?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 10.sp, color = Color.Gray) }
                    }
                }
            }
        }
    }
}

/** 检字·部首：现代彝文 26 部首 + 古彝文 67 部首 */
@Composable
fun RadicalHub(st: Store) {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubChip("现代彝文", kind == 0) { kind = 0 }
            SubChip("古彝文", kind == 1) { kind = 1 }
        }
        Box(Modifier.weight(1f)) { if (kind == 0) RadicalPane() else AncientRadicalPane(st) }
    }
}

@Composable
fun YiCharDialog(c: Char, r: Repo.Rad?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("彝字详情") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                YiText(c.toString(), 56.sp)
                val ci = Repo.chars[c]
                Text(I18n.f("凉山拼音：{0}{1}", ci?.py ?: Repo.rom(c) ?: "-", r?.let { I18n.f("    部首：第{0}部", it.no) } ?: ""), fontSize = 14.sp)
                if (ci != null && ci.ipa.isNotEmpty()) Text(I18n.f("国际拼音：{0}", ci.ipa), fontSize = 14.sp)
                if (ci != null && ci.mean.isNotEmpty()) Text(I18n.f("释义：{0}", ci.mean), fontSize = 14.sp)
                if (ci != null && ci.cat.isNotEmpty()) Text(I18n.f("类别：{0}", ci.cat), fontSize = 12.sp, color = Color.Gray)
                val ws = Repo.wordsWith(c)
                Text(if (ws.isEmpty()) "词典例词：暂无" else "词典例词：", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                ws.forEach { w -> Column(Modifier.padding(vertical = 3.dp)) { YiText(w.yi, 20.sp); Text(w.zh, fontSize = 14.sp) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { Clip.copy(ctx, c.toString()) }) { Text("复制") } }
    )
}

@Composable
fun RadicalPane() {
    var sel by rememberSaveable { mutableIntStateOf(-1) }
    var detail by remember { mutableStateOf<Char?>(null) }
    val r = Repo.radicals.getOrNull(sel)
    if (r == null) {
        Column {
            Text(I18n.f("彝文部首检字法：共 {0} 个部首，点选部首，再按笔画数找字", Repo.radicals.size), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(horizontal = 8.dp)) {
                itemsIndexed(Repo.radicals) { i, x ->
                    Card(Modifier.padding(4.dp).clickable { sel = i }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${x.no}", fontSize = 11.sp, color = Color.Gray)
                            YiText(x.rad, 28.sp, py = false)
                            Text(I18n.f("{0} 字", x.groups.sumOf { it.second.length }), fontSize = 10.sp, color = Red)
                        }
                    }
                }
            }
        }
    } else {
        Column {
            BackBar(I18n.f("第 {0} 部首 · {1} 字", r.no, r.groups.sumOf { it.second.length }), { sel = -1 }) { YiText(r.rad, 24.sp, py = false) }
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                r.groups.forEach { (n, chars) ->
                    item {
                        Text(if (n == 0) I18n.f("部首本身 · {0} 字", chars.length) else I18n.f("{0} 画 · {1} 字", n, chars.length), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp))
                        FlowRow {
                            chars.forEach { c ->
                                Column(Modifier.padding(3.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { detail = c }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.toString(), fontSize = 28.sp, fontFamily = Fonts.family)
                                    Text(Repo.rom(c) ?: "", fontSize = 10.sp, color = PyColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { c -> YiCharDialog(c, r) { detail = null } }
}


/** 2008 词典词条详情（WebView，带发音按钮；彝文逐字标拼音） */
@Composable
fun MdxDetail(detail: Pair<MdictFile, Int>, onOpen: (Pair<MdictFile, Int>?) -> Unit) {
    val (d, i0) = detail
    val i = remember(d, i0) { Mdict.resolve(d, i0) }
    val ctx = LocalContext.current
    val snds = remember(d, i) { if (Repo.hasYi(d.keys[i])) Mdict.sounds(d.keys[i]) else emptyList() }
    Column(Modifier.fillMaxSize()) {
        BackBar(d.keys[i].take(30), { onOpen(null) }) { TextButton(onClick = { Clip.copy(ctx, d.keys[i]) }) { Text("复制词") } }
        if (snds.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                snds.forEach { s -> OutlinedButton(onClick = { playSound(ctx, s) }) { Text(I18n.f("▶ 发音 · {0}", s.label), fontSize = 13.sp) } }
            }
        }
        MdxWebView(Mdict.html(d, i)) { w ->
            for (dd in Mdict.dicts) { val j = dd.exact(w); if (j >= 0) { onOpen(dd to j); break } }
        }
    }
}

private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "css" -> "text/css"; "js" -> "application/javascript"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "mp3" -> "audio/mpeg"; "woff" -> "font/woff"; "ttf" -> "font/ttf"
    else -> "application/octet-stream"
}

@Composable
fun MdxWebView(html: String, onEntry: (String) -> Unit) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url ?: return null
                    if (u.host != "mdict.local") return null
                    val p = u.path ?: ""
                    if (p == "/__yifont.ttf") return Fonts.bytes()?.let { WebResourceResponse("font/ttf", null, ByteArrayInputStream(it)) }
                    if (p == "/__ancient.ttf") return WebResourceResponse("font/ttf", null, ByteArrayInputStream(Fonts.ancientBytes()))
                    val path = "\\" + p.removePrefix("/").replace("/", "\\")
                    val b = Mdict.resource(path) ?: Mdict.resource(path.removePrefix("\\")) ?: return null
                    return WebResourceResponse(mimeOf(path), null, ByteArrayInputStream(b))
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url ?: return false
                    if (u.scheme == "entry") { onEntry(Uri.decode(u.toString().removePrefix("entry://"))); return true }
                    return u.host != "mdict.local"
                }
            }
        }
    }, update = {
        val key = html + Fonts.idx
        if (it.tag != key) { it.tag = key; it.loadDataWithBaseURL("https://mdict.local/", html, "text/html", "UTF-8", null) }
    })
}

fun playSound(ctx: Context, s: Mdict.Snd) {
    try {
        val f = File(ctx.cacheDir, "snd.mp3")
        f.writeBytes(s.mdd.record(s.idx))
        MediaPlayer().apply {
            setDataSource(f.path); prepare(); start()
            setOnCompletionListener { it.release() }
        }
    } catch (_: Throwable) {}
}
