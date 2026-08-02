#!/usr/bin/env python3
"""Save fixtures (index + one article) for the M6 batch of new sources.

FORCED DIRECT (trust_env=False). Reads probe_report.json for article URLs,
re-downloads full pages and writes sources/fixtures/<id>/{index.xml,article.html}.
"""
import io
import json
import sys
from pathlib import Path

import requests

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
FIX = HERE.parent.parent / "sources" / "fixtures"
REPORT = json.loads((HERE / "probe_report.json").read_text(encoding="utf-8"))

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TIMEOUT = 30

IDS = ["rte", "daily_mirror", "moscowtimes_en", "nasa", "livescience",
       "quanta_magazine", "freenature", "techcrunch", "propublica", "new_scientist"]

session = requests.Session()
session.trust_env = False
session.headers.update({"User-Agent": UA})

urls = json.loads((FIX / "article_urls.json").read_text(encoding="utf-8"))

for sid in IDS:
    entry = REPORT[sid]
    d = FIX / sid
    d.mkdir(parents=True, exist_ok=True)
    feed = session.get(entry["feed"], timeout=TIMEOUT)
    feed.raise_for_status()
    (d / "index.xml").write_text(feed.text, encoding="utf-8")
    art_url = entry["article_url"]
    art = session.get(art_url, timeout=TIMEOUT)
    art.raise_for_status()
    (d / "article.html").write_text(art.text, encoding="utf-8")
    urls[sid] = art_url
    print(f"{sid:18s} index={len(feed.text):8d}B  article={len(art.text):8d}B  {art_url[:70]}")

(FIX / "article_urls.json").write_text(
    json.dumps(urls, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("article_urls.json updated")
