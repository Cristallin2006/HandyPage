#!/usr/bin/env python3
"""Extract metadata from calibre .recipe files via AST (no execution).

Outputs recipes_meta.json with, per recipe:
  id, title, language, description, feed_urls, homepage, kind, flags
kind: 'rss' (has static feeds list) | 'parse_index' (custom index parser) | 'unknown'
flags: webengine, needs_subscription, from_web, no_language
"""
import ast
import json
import sys
from pathlib import Path

RECIPES_DIR = Path(sys.argv[1] if len(sys.argv) > 1 else
                  r"C:/Users/Lenovo/Desktop/calibre-master/calibre-master/recipes")
OUT = Path(__file__).with_name("recipes_meta.json")


def const_str(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


def const_bool(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, bool):
        return node.value
    return None


def extract_feeds(node):
    """feeds = [('name', 'url'), ...] or ['url', ...] -> list of (name|None, url)"""
    urls = []
    if not isinstance(node, (ast.List, ast.Tuple)):
        return urls
    for el in node.elts:
        if isinstance(el, (ast.List, ast.Tuple)) and len(el.elts) >= 2:
            name = const_str(el.elts[0])
            url = const_str(el.elts[1])
            if url and url.startswith("http"):
                urls.append((name, url))
        else:
            url = const_str(el)
            if url and url.startswith("http"):
                urls.append((None, url))
    return urls


def first_http_in_func(func):
    for n in ast.walk(func):
        s = const_str(n)
        if s and s.startswith("http") and len(s) < 300 and "{" not in s:
            return s
    return None


def parse_recipe(path):
    try:
        tree = ast.parse(path.read_bytes())
    except Exception as e:
        return {"id": path.stem, "error": f"ast: {e}"}

    recipe_class = None
    for node in tree.body:
        if isinstance(node, ast.ClassDef):
            for base in node.bases:
                name = ""
                if isinstance(base, ast.Name):
                    name = base.id
                elif isinstance(base, ast.Attribute):
                    name = base.attr
                if name.endswith("Recipe"):
                    recipe_class = node
                    break
            if recipe_class:
                break
    if recipe_class is None:
        return {"id": path.stem, "error": "no recipe class"}

    meta = {"id": path.stem, "title": None, "language": None, "description": None,
            "feed_urls": [], "homepage": None, "kind": "unknown", "flags": []}
    has_parse_index = False
    parse_index_url = None

    for stmt in recipe_class.body:
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1 and isinstance(stmt.targets[0], ast.Name):
            attr = stmt.targets[0].id
            val = stmt.value
            if attr == "title":
                meta["title"] = const_str(val)
            elif attr == "language":
                meta["language"] = const_str(val)
            elif attr == "description":
                meta["description"] = const_str(val)
            elif attr == "feeds":
                meta["feed_urls"] = extract_feeds(val)
            elif attr == "browser_type" and const_str(val) == "webengine":
                meta["flags"].append("webengine")
            elif attr == "needs_subscription" and const_bool(val):
                meta["flags"].append("needs_subscription")
            elif attr == "from_web" and const_bool(val):
                meta["flags"].append("from_web")
        elif isinstance(stmt, ast.FunctionDef) and stmt.name == "parse_index":
            has_parse_index = True
            parse_index_url = first_http_in_func(stmt)

    if meta["feed_urls"]:
        meta["kind"] = "rss"
    elif has_parse_index:
        meta["kind"] = "parse_index"
        if parse_index_url:
            meta["homepage"] = parse_index_url
    if meta["language"] is None:
        meta["flags"].append("no_language")
    meta["title"] = meta["title"] or meta["id"]
    return meta


def main():
    out = []
    for p in sorted(RECIPES_DIR.glob("*.recipe")):
        out.append(parse_recipe(p))
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    total = len(out)
    rss = sum(1 for m in out if m.get("kind") == "rss")
    pi = sum(1 for m in out if m.get("kind") == "parse_index")
    en = sum(1 for m in out if (m.get("language") or "").startswith("en"))
    nolang = sum(1 for m in out if "no_language" in m.get("flags", []))
    print(f"total={total} rss={rss} parse_index={pi} english={en} no_language={nolang} errors={sum(1 for m in out if 'error' in m)}")


if __name__ == "__main__":
    main()
