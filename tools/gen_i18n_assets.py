"""扫描源码里所有中文界面字符串（含模板），生成 assets/ui_all.json（界面字符串登记表）、更新 ui_yi.json，
并把还没有彝文译文的字符串导出为 no_translation.json（中文 + 英文，交给彝文翻译 AI 翻译后以 json 返回）。
用法: python3 tools/gen_i18n_assets.py <mdx文件> [no_translation.json输出路径]"""
import json, re, sys, glob, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from i18n_convert_lib import literals
HAN = re.compile(r'[\u4e00-\u9fff]')
A = 'app/src/main/assets/'
src_dir = 'app/src/main/java/com/ddopmago/'
TOK = re.compile(r'《([^》]*)》|「([^」]*)」|\d+|[\ua000-\ua48c]+')
def norm(s):
    n = [0]
    def r(m):
        i = n[0]; n[0] += 1
        if m.group(0).startswith('《'): return '《{%d}》' % i
        if m.group(0).startswith('「'): return '「{%d}」' % i
        return '{%d}' % i
    return TOK.sub(r, s)
def unesc(s):
    return s.replace('\\"', '"').replace('\\\\', '\\').replace('\\$', '$')
R = set()
for f in glob.glob(src_dir + '*.kt'):
    if f.endswith('Print.kt'): continue
    for lit in literals(open(f, encoding='utf-8').read()):
        s = unesc(lit)
        if HAN.search(s) and '\\' not in s and len(s) <= 140 and '$' not in s: R.add(s)
for q in json.load(open(A + 'bookqs.json', encoding='utf-8')):
    if HAN.search(q['prompt']): R.add(norm(q['prompt']))
en = json.load(open(A + 'ui_en.json', encoding='utf-8'))
extra = json.load(open('tools/ui_en_extra.json', encoding='utf-8')) if os.path.exists('tools/ui_en_extra.json') else {}
en.update(extra)
R |= set(en.keys())
# 彝文：1) 规范彝文词典 例词 2) 彝汉英常用词词汇(2008) 汉彝索引
from mdreader import M
dictj = json.load(open(A + 'dict.json', encoding='utf-8'))
auth = {}
for w, g in dictj:
    g = g.strip()
    if g and g not in auth: auth[g] = w
m = M(sys.argv[1]); idx = {}
for i, k in enumerate(m.keys):
    if HAN.search(k) and not any('\ua000' <= c <= '\ua48c' for c in k):
        ys = re.findall(r'entry://([\ua000-\ua48c]+)', m.rec(i).decode('utf-8', 'replace'))
        if ys and k not in idx: idx[k] = ys[0]
old = json.load(open(A + 'ui_yi.json', encoding='utf-8'))
yi = {k: v for k, v in old.items() if k in R}
for s in R:
    key = s.strip().rstrip('：:。…')
    if '{' in s or len(key) < 2 and key != '词': continue
    if key in auth: yi.setdefault(s, auth[key])
    elif key in idx: yi.setdefault(s, idx[key])
# 彝文翻译 AI 返回的译文（来源权威性最低，不覆盖词典里已有的彝文词）
if os.path.exists('tools/ui_yi_returned.json'):
    for k, v in json.load(open('tools/ui_yi_returned.json', encoding='utf-8')).items():
        if k in R and v and k not in yi: yi[k] = v
json.dump(sorted(R), open(A + 'ui_all.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(yi, open(A + 'ui_yi.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(en, open(A + 'ui_en.json', 'w', encoding='utf-8'), ensure_ascii=False)
miss = [s for s in sorted(R, key=lambda x: (len(x), x)) if s not in yi]
out = [dict(id=i + 1, zh=s, en=en.get(s, '')) for i, s in enumerate(miss)]
json.dump(dict(说明='把每条的 zh（简体中文）和 en（英文）翻译成凉山彝文，填进 yi 字段，原样以 json 返回。{0}{1} 是占位符（会被数字或名称替换），请保留在译文里。', items=[dict(o, yi='') for o in out]),
          open(sys.argv[2] if len(sys.argv) > 2 else 'no_translation.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(len(R), 'strings;', len(yi), 'have Yi;', len(miss), 'need translation;', sum(1 for o in out if not o['en']), 'without English')
print([o['zh'] for o in out if not o['en']])
