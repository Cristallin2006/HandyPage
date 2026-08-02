#!/usr/bin/env python3
"""Save fixtures (index + one article) for the M18 batch of new sources.

FORCED DIRECT (trust_env=False). Writes sources/fixtures/<id>/index.*
and article.html, and registers the picked URL in article_urls.json.
Article picks are curated (not feed-first): spectrum's feed-first item is a
300-char podcast stub and lightspeed's is a Kickstarter announcement —
both would make lousy regression fixtures.
"""
import io
import json
import re
import sys
from pathlib import Path

import requests
from bs4 import BeautifulSoup

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
FIX = HERE.parent.parent / "sources" / "fixtures"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TIMEOUT = 30

# id -> (index kind, index url, article pick rule)
# rss_first: first <item><link>; html_regex: first archive link matching regex;
# rss_title: first item whose title contains the substring (real long-form pick).
SPECS = {
    "apod": {
        "kind": "html",
        "index": "https://apod.nasa.gov/apod/archivepix.html",
        "pick": ("html_regex", r"ap\d{6}\.html$"),
    },
    "nautilus": {
        "kind": "rss",
        "index": "https://nautil.us/feed/",
        "pick": ("rss_first", None),
    },
    "ieeespectrum": {
        "kind": "rss",
        "index": "https://spectrum.ieee.org/rss/fulltext",
        "pick": ("rss_title", "fiber"),  # skip podcast stubs, take a real feature
    },
    "lightspeed": {
        "kind": "rss",
        "index": "https://www.lightspeedmagazine.com/rss-2/",
        "pick": ("rss_title", "Discarded God"),  # a fiction piece, not an announcement
    },
}

session = requests.Session()
session.trust_env = False
session.headers.update({"User-Agent": UA})

urls = json.loads((FIX / "article_urls.json").read_text(encoding="utf-8"))

import xml.etree.ElementTree as ET

for sid, spec in SPECS.items():
    d = FIX / sid
    d.mkdir(parents=True, exist_ok=True)
    idx = session.get(spec["index"], timeout=TIMEOUT)
    idx.raise_for_status()
    ext = "xml" if spec["kind"] == "rss" else "html"
    (d / f"index.{ext}").write_text(idx.text, encoding="utf-8")

    mode, arg = spec["pick"]
    link = None
    if spec["kind"] == "rss":
        root = ET.fromstring(idx.text.encode("utf-8"))
        for item in root.iter("item"):
            title = (item.findtext("title") or "")
            l = (item.findtext("link") or "").strip()
            if not l:
                continue
            if mode == "rss_first" or (mode == "rss_title" and arg.lower() in title.lower()):
                link = l
                break
    else:
        soup = BeautifulSoup(idx.text, "html.parser")
        rx = re.compile(arg)
        for a in soup.select("b a"):
            href = a.get("href", "")
            if rx.search(href):
                from urllib.parse import urljoin
                link = urljoin(spec["index"], href)
                break
    if not link:
        print(f"[FAIL] {sid}: no article link picked")
        continue

    art = session.get(link, timeout=TIMEOUT)
    art.raise_for_status()
    (d / "article.html").write_text(art.text, encoding="utf-8")
    urls[sid] = link
    print(f"{sid:18s} index={len(idx.text):8d}B  article={len(art.text):8d}B  {link[:70]}")

(FIX / "article_urls.json").write_text(
    json.dumps(urls, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("article_urls.json updated")
