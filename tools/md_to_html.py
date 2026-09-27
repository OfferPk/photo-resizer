#!/usr/bin/env python3
"""Tiny Markdown -> HTML converter for PRIVACY.md (headings, lists, bold, code, links)."""
import re, sys, html

def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"(?<![\w*])_([^_]+)_(?!\w)", r"<em>\1</em>", s)
    s = re.sub(r"&lt;(https?://[^&]+)&gt;", r'<a href="\1">\1</a>', s)
    s = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', s)
    return s

def convert(md):
    out, in_list, para = [], False, []
    def flush():
        nonlocal para
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>")
            para = []
    for line in md.splitlines():
        if not line.strip():
            flush()
            if in_list: out.append("</ul>"); in_list = False
            continue
        m = re.match(r"^(#{1,3}) (.*)", line)
        if m:
            flush()
            if in_list: out.append("</ul>"); in_list = False
            n = len(m.group(1)); out.append(f"<h{n}>{inline(m.group(2))}</h{n}>"); continue
        if line.startswith("- "):
            flush()
            if not in_list: out.append("<ul>"); in_list = True
            out.append("<li>" + inline(line[2:]) + "</li>"); continue
        para.append(line.strip())
    flush()
    if in_list: out.append("</ul>")
    return "\n".join(out)

body = convert(open(sys.argv[1]).read())
print(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy Policy – Photo Resizer: KB &amp; Passport</title>
<link rel="icon" href="icon-512.png">
<style>
body{{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:760px;margin:0 auto;padding:24px 18px 60px;line-height:1.6;color:#1b1f24;background:#f7f9fc}}
h1{{font-size:1.7rem;line-height:1.25}} h2{{margin-top:2rem;font-size:1.2rem;color:#0b5cad}}
code{{background:#e8edf5;padding:1px 5px;border-radius:4px}} a{{color:#0b5cad}}
header{{display:flex;align-items:center;gap:12px;margin-bottom:8px}} header img{{width:48px;height:48px;border-radius:12px}}
</style></head><body>
<header><img src="icon-512.png" alt=""><a href="./">Photo Resizer: KB &amp; Passport</a></header>
{body}
</body></html>""")
