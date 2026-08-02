#!/usr/bin/env python3
"""Fetch real pages as test fixtures for Handypage sources.

All requests are FORCED DIRECT (trust_env=False): the first-tier sources must
work for CN users without proxy. Run with the system proxy OFF to prove it.

Usage:
  python fetch_fixtures.py index     # fetch index pages/feeds for all sources
  python fetch_fixtures.py links <id>  # list article-link candidates from saved index
  python fetch_fixtures.py articles  # fetch 1 article page per source
"""
import io
import json
import re
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
FIX = HERE.parent.parent / "sources" / "fixtures"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TIMEOUT = 25

# index.kind: "rss" -> parse <item><link>; "html" -> use link_css + link_regex
SOURCES = {
    "npr": {
        "name": "NPR",
        "index": {"kind": "rss", "url": "https://feeds.npr.org/1001/rss.xml"},
    },
    "korea_herald": {
        "name": "Korea Herald",
        # 2011 recipe feeds (rss/020100000000.xml etc.) now return empty channels;
        # current working feed found on homepage:
        "index": {"kind": "rss", "url": "https://www.koreaherald.com/rss/newsAll"},
    },
    "china_daily": {
        "name": "China Daily",
        # chinadaily.com.cn/rss/*.xml all abandoned (2017-2019 content);
        # use the HTML section index instead (fresh /a/YYYYMM/DD/WS... links).
        "index": {"kind": "html", "url": "https://www.chinadaily.com.cn/world"},
    },
    "breaking_news_english": {
        "name": "Breaking News English",
        "index": {"kind": "html", "url": "https://breakingnewsenglish.com/",
                  # filled after inspecting the homepage:
                  "link_css": None, "link_regex": None},
    },
    "news_in_levels": {
        "name": "News in Levels",
        "index": {"kind": "html", "url": "https://www.newsinlevels.com/",
                  "link_css": None, "link_regex": None},
    },
}

# per-source overrides for picking the article URL (set after `links` inspection)
ARTICLE_PICK = {
    # BNE homepage links look like "2607/260716-knee-surgery.html" (main page)
    # plus "-0/-1/-2/-4/-5" level variants — take the main page only.
    "breaking_news_english": {
        "css": "a[href]",
        "regex": r"^\d{4}/\d{6}-[\w-]+(?<!-\d)\.html$",
    },
    # NiL article links: /products/<slug>-level-1/ (levels 1-3; take level 1,
    # other levels derivable by string replacement later).
    "news_in_levels": {
        "css": "a[href*='/products/']",
        "regex": r"-level-1/?$",
    },
    # China Daily /world index: protocol-relative links //host/a/202607/20/WS....html
    "china_daily": {
        "css": "a[href*='/a/']",
        "regex": r"/a/\d{6}/\d{2}/WS[0-9a-f]+\.html$",
    },
}


def session():
    s = requests.Session()
    s.trust_env = False  # force direct connection, ignore any proxy env
    s.headers.update({"User-Agent": UA, "Accept": "*/*"})
    return s


def get(s, url):
    t0 = time.monotonic()
    r = s.get(url, timeout=TIMEOUT, allow_redirects=True)
    ms = int((time.monotonic() - t0) * 1000)
    r.raise_for_status()
    return r, ms


def fetch_indexes():
    s = session()
    for sid, cfg in SOURCES.items():
        d = FIX / sid
        d.mkdir(parents=True, exist_ok=True)
        url = cfg["index"]["url"]
        try:
            r, ms = get(s, url)
            ext = "xml" if cfg["index"]["kind"] == "rss" else "html"
            out = d / f"index.{ext}"
            out.write_bytes(r.content)
            print(f"[ok] {sid:24s} {ms:5d}ms {len(r.content):8d}B -> {out.name}  ({r.url})")
        except Exception as e:
            print(f"[FAIL] {sid:24s} {url} -> {type(e).__name__}: {e}")


def first_rss_link(index_xml: Path):
    root = ET.fromstring(index_xml.read_bytes())
    for item in root.iter("item"):
        link = item.findtext("link")
        if link and link.strip():
            return link.strip()
    # atom fallback
    ns = {"a": "http://www.w3.org/2005/Atom"}
    for entry in root.iter("{http://www.w3.org/2005/Atom}entry"):
        link = entry.find("a:link", ns)
        if link is not None and link.get("href"):
            return link.get("href")
    return None


def list_links(sid):
    cfg = SOURCES[sid]
    idx = FIX / sid / "index.html"
    soup = BeautifulSoup(idx.read_bytes(), "lxml")
    seen = set()
    for a in soup.find_all("a", href=True):
        href = a["href"]
        text = a.get_text(" ", strip=True)[:60]
        if href in seen or href.startswith(("#", "javascript:", "mailto:")):
            continue
        seen.add(href)
        print(f"{href[:100]:100s}  {text}")


def fetch_articles():
    s = session()
    picks = {}
    for sid, cfg in SOURCES.items():
        d = FIX / sid
        link = None
        if cfg["index"]["kind"] == "rss":
            link = first_rss_link(d / "index.xml")
        elif sid in ARTICLE_PICK:
            rule = ARTICLE_PICK[sid]
            soup = BeautifulSoup((d / "index.html").read_bytes(), "lxml")
            for a in soup.select(rule["css"]):
                href = a.get("href", "")
                if re.search(rule["regex"], href):
                    link = href
                    break
        if not link:
            print(f"[skip] {sid}: no article link rule yet")
            continue
        link = urljoin(cfg["index"]["url"], link)
        try:
            r, ms = get(s, link)
            out = d / "article.html"
            out.write_bytes(r.content)
            picks[sid] = link
            print(f"[ok] {sid:24s} {ms:5d}ms {len(r.content):8d}B  {link[:90]}")
        except Exception as e:
            print(f"[FAIL] {sid:24s} {link[:80]} -> {type(e).__name__}: {e}")
    (FIX / "article_urls.json").write_text(
        json.dumps(picks, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "index"
    if cmd == "index":
        fetch_indexes()
    elif cmd == "links":
        list_links(sys.argv[2])
    elif cmd == "articles":
        fetch_articles()
