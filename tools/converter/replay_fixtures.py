#!/usr/bin/env python3
"""Replay saved fixtures against source.json rules (dev-side regression test).

Mirrors what the Android DSL engine will do: parse the index, derive the
article list, then extract title + clean body from the article page.
Runs offline against sources/fixtures/<id>/ so a site redesign that breaks a
selector is caught by re-fetching fixtures and re-running this script.

Usage: python replay_fixtures.py [source_id ...]
Exit code 0 = all sources pass.
"""
import io
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urljoin

from bs4 import BeautifulSoup

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
SOURCES_DIR = ROOT / "sources"

MIN_ARTICLE_CHARS = 300
MIN_INDEX_ITEMS = 3


def humanize_slug(url: str) -> str:
    slug = re.sub(r"\.html?$", "", url.rstrip("/").split("/")[-1])
    slug = re.sub(r"-level-\d+$", "", slug)
    slug = re.sub(r"^\d{6}-", "", slug)  # BNE date prefix
    return slug.replace("-", " ").strip().capitalize()


def parse_index(cfg: dict, idx_path: Path):
    """Return list of {title, url} from the saved index fixture."""
    ind = cfg["index"]
    items = []
    if ind["type"] == "rss":
        root = ET.fromstring(idx_path.read_bytes())
        for item in root.iter("item"):
            link = (item.findtext("link") or "").strip()
            title = (item.findtext("title") or "").strip()
            if link:
                items.append({"title": title or humanize_slug(link), "url": link})
    else:
        soup = BeautifulSoup(idx_path.read_bytes(), "lxml")
        rx = re.compile(ind["link_regex"])
        best = {}  # url -> longest anchor text (image links carry no text)
        for a in soup.select(ind["link_css"]):
            href = a.get("href", "")
            if not rx.search(href):
                continue
            url = urljoin(ind["url"], href)
            text = a.get_text(" ", strip=True)
            if len(text) > len(best.get(url, "")):
                best[url] = text
        for url, text in best.items():
            items.append({"title": text or humanize_slug(url), "url": url})
    return items[: ind.get("max", 20)]


def parse_article(cfg: dict, art_path: Path):
    soup = BeautifulSoup(art_path.read_bytes(), "lxml")
    art = cfg["article"]
    title_el = soup.select_one(art["title"]) if art.get("title") else None
    title = title_el.get_text(" ", strip=True) if title_el else None
    content = soup.select_one(art["content"])
    if content is None:
        return title, None, 0
    for sel in art.get("remove", []):
        for el in content.select(sel):
            el.decompose()
    for el in content.select("script, style, iframe, noscript"):
        el.decompose()
    text = content.get_text(" ", strip=True)
    n_imgs = len(content.find_all("img"))
    return title, text, n_imgs


def check_source(sid: str) -> bool:
    cfg = json.loads((SOURCES_DIR / f"{sid}.json").read_text(encoding="utf-8"))
    fix = SOURCES_DIR / cfg["test"]["fixture"]
    ok = True

    idx_file = next(fix.glob("index.*"), None)
    art_file = fix / "article.html"
    if not idx_file or not art_file.exists():
        print(f"[FAIL] {sid}: missing fixtures in {fix}")
        return False

    items = parse_index(cfg, idx_file)
    n = len(items)
    status = "ok " if n >= MIN_INDEX_ITEMS else "FAIL"
    ok &= n >= MIN_INDEX_ITEMS
    print(f"[{status}] {sid:24s} index items: {n:3d}  e.g. {items[0]['title'][:50] if items else '-'}")

    title, text, n_imgs = parse_article(cfg, art_file)
    chars = len(text or "")
    good_title = bool(title and len(title) >= 5)
    good_body = chars >= MIN_ARTICLE_CHARS
    ok &= good_title and good_body
    print(f"[{'ok ' if good_title else 'FAIL'}] {sid:24s} title: {(title or '-')[:70]}")
    print(f"[{'ok ' if good_body else 'FAIL'}] {sid:24s} body chars: {chars:5d}, imgs: {n_imgs}")
    if text:
        print(f"       preview: {text[:130]}")
    return ok


if __name__ == "__main__":
    ids = sys.argv[1:] or sorted(p.stem for p in SOURCES_DIR.glob("*.json"))
    results = {sid: check_source(sid) for sid in ids}
    passed = sum(results.values())
    print(f"\n{passed}/{len(results)} sources PASS")
    sys.exit(0 if passed == len(results) else 1)
