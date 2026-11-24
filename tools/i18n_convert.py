"""把界面里带 $ 的中文字符串模板改成 I18n.f("模板 {0}", 参数)，使整句可以被翻译成彝文/英文。
用法: python3 tools/i18n_convert.py <Kotlin文件...>   （一次性重构；已转换的文件再运行不会变化）"""
import re, sys
HAN = re.compile(r'[\u4e00-\u9fff]')
def scan(src):
    """返回顶层字符串字面量 (start, end, content)；正确处理 ${...} 里嵌套的字符串"""
    out = []; i = 0; n = len(src)
    def skip_expr(j):  # j 在 '${' 之后，返回匹配的 '}' 之后
        depth = 1
        while j < n and depth > 0:
            c = src[j]
            if c == '"': j = skip_str(j + 1); continue
            if c == '{': depth += 1
            elif c == '}': depth -= 1
            j += 1
        return j
    def skip_str(j):  # j 在开头引号之后，返回结尾引号之后
        while j < n and src[j] != '"':
            if src[j] == '\\': j += 2; continue
            if src[j] == '$' and j + 1 < n and src[j + 1] == '{': j = skip_expr(j + 2); continue
            j += 1
        return j + 1
    while i < n:
        c = src[i]
        if c == '/' and src[i:i + 2] == '//':
            while i < n and src[i] != '\n': i += 1
        elif c == '/' and src[i:i + 2] == '/*':
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2
        elif c == "'":
            j = i + 1
            j += 2 if src[j] == '\\' else 1
            i = j + 1
        elif c == '"':
            e = skip_str(i + 1); out.append((i, e, src[i + 1:e - 1])); i = e
        else: i += 1
    return out
def template(c):
    buf = ''; args = []; i = 0
    while i < len(c):
        ch = c[i]
        if ch == '\\': buf += c[i:i + 2]; i += 2; continue
        if ch == '$' and i + 1 < len(c) and c[i + 1] == '{':
            depth = 1; j = i + 2; ins = False
            while j < len(c) and depth > 0:
                x = c[j]
                if ins:
                    if x == '\\': j += 2; continue
                    if x == '"': ins = False
                else:
                    if x == '"': ins = True
                    elif x == '{': depth += 1
                    elif x == '}': depth -= 1
                j += 1
            args.append(c[i + 2:j - 1]); buf += '{%d}' % (len(args) - 1); i = j; continue
        if ch == '$' and i + 1 < len(c) and (c[i + 1].isalpha() or c[i + 1] == '_'):
            j = i + 1
            while j < len(c) and (c[j].isalnum() or c[j] == '_'): j += 1
            args.append(c[i + 1:j]); buf += '{%d}' % (len(args) - 1); i = j; continue
        buf += ch; i += 1
    return buf, args
total = 0
for path in sys.argv[1:]:
    src = open(path, encoding='utf-8').read()
    edits = []
    for s, e, c in scan(src):
        if '$' not in c: continue
        buf, args = template(c)
        if not args or not HAN.search(re.sub(r'\{\d+\}', '', buf)): continue
        if re.search(r'[{}]', re.sub(r'\{\d+\}', '', buf)): continue
        edits.append((s, e, 'I18n.f("%s", %s)' % (buf, ', '.join(args))))
    for s, e, r in reversed(edits): src = src[:s] + r + src[e:]
    open(path, 'w', encoding='utf-8').write(src); total += len(edits)
    print(path, len(edits))
print('total', total)
