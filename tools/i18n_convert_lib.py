import re
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

def literals(src):
    return [c for (_s, _e, c) in scan(src)]
