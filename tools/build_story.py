"""用修改后的《凉山民间故事选》.docx（extract-text 后的 md）替换 books.json 里的 story，并生成「人物志」。
用法: python3 tools/build_story.py story_new.md"""
import re, json, sys
t = open(sys.argv[1], encoding='utf-8').read()
def norm(s): return re.sub(r'[^\u4e00-\u9fffA-Za-z0-9]+', '', s)
L = [l.rstrip() for l in t.split('\n')]
ti = next(i for i, l in enumerate(L) if re.fullmatch(r'目\s*录', l.strip()))
parts = ['神话与传说', '彝族古代名人故事', '机智人物吉潘瓦先的故事', '动物故事动物与人的故事', '其他故事']
entries = []; cur = parts[0]
for l in L[ti + 1:ti + 400]:
    n = norm(l)
    if n in parts: cur = n; continue
    for m in re.finditer(r'([^\d/\n][^/\n]*?)\s*/\s*(\d+)', l):
        title = m.group(1).strip()
        if norm(title) in ('序言', '目录', ''): continue
        entries.append((title, cur))
last = max(i for i in range(ti, ti + 400) if re.search(r'/\s*\d+', L[i]))
pos = last + 1; found = []
for title, part in entries:
    nt = norm(title); k = None
    for i in range(pos, len(L)):
        if norm(L[i]) == nt: k = i; break
    if k is None:
        for i in range(pos, len(L)):
            if len(nt) >= 4 and norm(L[i]).startswith(nt[:4]) and len(norm(L[i])) <= len(nt) + 6: k = i; break
    if k is None:  # 标题行缺失：从第一次提到该标题名的段落开始
        for i in range(pos, len(L)):
            if len(nt) >= 3 and nt in norm(L[i]) and len(L[i]) > 20: k = i - 1; break
    found.append((title, part, k))
    if k is not None: pos = k + 1
ok = [f for f in found if f[2] is not None]
print('matched', len(ok), 'of', len(found), 'missing', [f[0] for f in found if f[2] is None])
chs = []
for n, (title, part, k) in enumerate(ok):
    e = ok[n + 1][2] if n + 1 < len(ok) else len(L)
    body = [x.strip() for x in L[k + 1:e] if x.strip() and not re.fullmatch(r'\d{1,3}', x.strip()) and norm(x) not in parts]
    chs.append(dict(t=re.sub(r'\s+', ' ', title).replace('( ', '(').replace(' )', ')'), part=part.replace('动物故事动物与人的故事', '动物故事·动物与人的故事'), text='\n'.join(body)))
A = 'app/src/main/assets/'
books = [b for b in json.load(open(A + 'books.json', encoding='utf-8')) if b['id'] not in ('story', 'renwu')]
books.append(dict(id='story', title='凉山民间故事选', lang='zh', chapters=chs))
persons = [('支呷阿鲁', '男'), ('惹底索夫', '男'), ('阿苏拉者', '男'), ('吉尼懂子', '男'), ('阿苏斯惹', '男'), ('罕叶迪谷', '男'), ('阿依冲品', '男'), ('呷嫫阿牛', '女'), ('兹兹尼渣', '女'), ('布阿诗呷薇', '女'), ('阿丝木嘎', '女'), ('吉潘瓦先', '机智人物')]
rc = []
for name, sex in persons:
    if name == '吉潘瓦先': rel = [c for c in chs if c['part'].startswith('机智人物')]
    else: rel = [c for c in chs if name in c['t'] and c['part'].startswith('彝族古代')] or [c for c in chs if name in c['t']]
    if not rel: continue
    kind = '男性名人' if sex == '男' else ('女性名人' if sex == '女' else '机智人物')
    body = ['%s（%s）' % (name, kind), '生平事迹，出自《凉山民间故事选》，共 %d 则。' % len(rel), '']
    for c in rel: body += ['【' + c['t'] + '】', c['text'], '']
    rc.append(dict(t=name + '（' + sex + '）', part='', text='\n'.join(body).strip()))
books.append(dict(id='renwu', title='人物志', lang='zh', chapters=rc))
json.dump(books, open(A + 'books.json', 'w', encoding='utf-8'), ensure_ascii=False)
print([(b['id'], len(b['chapters'])) for b in books], [c['t'] for c in rc])
