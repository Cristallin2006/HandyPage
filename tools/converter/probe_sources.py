#!/usr/bin/env python3
"""Probe candidate RSS sources: fetch feed + one article, suggest CSS selectors.

FORCED DIRECT (trust_env=False). Run with system proxy OFF.

Usage: python probe_sources.py [id ...]   (default: all candidates)
"""
import io
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import requests
from bs4 import BeautifulSoup

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
OUT = HERE / "probe_report.json"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TIMEOUT = 20

# id -> (display name, feed url, homepage)
CANDIDATES = {
    "foxnews": ("FOX News", "https://feeds.foxnews.com/foxnews/latest", "https://www.foxnews.com/"),
    "rte": ("RTE News", "https://www.rte.ie/rss/news.xml", "https://www.rte.ie/news/"),
    "daily_mirror": ("Daily Mirror", "https://www.mirror.co.uk/news/uk-news/rss.xml", "https://www.mirror.co.uk/"),
    "moscowtimes_en": ("The Moscow Times", "https://www.themoscowtimes.com/rss/news", "https://www.themoscowtimes.com/"),
    "mail_and_guardian": ("Mail & Guardian", "https://mg.co.za/rss/national/", "https://mg.co.za/"),
    "nasa": ("NASA", "https://www.nasa.gov/rss/dyn/breaking_news.rss", "https://www.nasa.gov/"),
    "livescience": ("Live Science", "https://www.livescience.com/feeds/all", "https://www.livescience.com/"),
    "quanta_magazine": ("Quanta Magazine", "https://api.quantamagazine.org/feed/", "https://www.quantamagazine.org/"),
    "freenature": ("Nature News", "https://feeds.nature.com/nature/rss/current", "https://www.nature.com/"),
    "techcrunch": ("TechCrunch", "https://techcrunch.com/feed/", "https://techcrunch.com/"),
    "the_verge": ("The Verge", "https://www.theverge.com/rss/index.xml", "https://www.theverge.com/"),
    "strange_horizons": ("Strange Horizons", "https://strangehorizons.com/feed/", "https://strangehorizons.com/"),
    "apod": ("Astronomy Picture of the Day", "https://apod.nasa.gov/apod.rss", "https://apod.nasa.gov/"),
    "cnetnews": ("CNET News", "https://www.cnet.com/rss/news/", "https://www.cnet.com/"),
    "endgadget": ("Engadget", "https://www.engadget.com/rss.xml", "https://www.engadget.com/"),
    "propublica": ("ProPublica", "https://feeds.propublica.org/propublica/main", "https://www.propublica.org/"),
    "new_scientist": ("New Scientist", "https://www.newscientist.com/section/news/feed/", "https://www.newscientist.com/"),
    # M18 second batch (2026-07-31 direct probe, all passed redirect-chain check)
    "phys_org": ("Phys.org", "https://phys.org/rss-feed/", "https://phys.org/"),
    "nautilus": ("Nautilus", "https://nautil.us/feed/", "https://nautil.us/"),
    "ieeespectrum": ("IEEE Spectrum", "https://spectrum.ieee.org/rss/fulltext", "https://spectrum.ieee.org/"),
    "lightspeed": ("Lightspeed Magazine", "https://www.lightspeedmagazine.com/rss-2/", "https://www.lightspeedmagazine.com/"),
}

SELECTOR_CANDIDATES = [
    "article",
    ".article-body",
    ".article-content",
    ".entry-content",
    ".post-content",
    "#article-body",
    ".story-body",
    ".c-entry-content",
    ".article__body",
    ".articleBody",
    "[itemprop=articleBody]",
    ".caas-body",
    ".body-copy",
    ".content-body",
    "main",
    "#content",
]

session = requests.Session()
session.trust_env = False
session.headers.update({"User-Agent": UA})


def get(url):
    r = session.get(url, timeout=TIMEOUT, allow_redirects=True)
    r.raise_for_status()
    return r


def feed_links(text):
    """Return (channel_title, [(title, link), ...]) from RSS/Atom text."""
    links = []
    try:
        root = ET.fromstring(text.encode("utf-8", "ignore"))
    except ET.ParseError:
        return "", []
    ch_title = ""
    for item in root.iter():
        tag = item.tag.split("}")[-1]
        if tag == "channel" or (tag == "feed" and not ch_title):
            for c in item:
                if c.tag.split("}")[-1] == "title" and c.text:
                    ch_title = c.text.strip()
                    break
        if tag in ("item", "entry"):
            title, link = "", ""
            for c in item:
                t = c.tag.split("}")[-1]
                if t == "title" and c.text and not title:
                    title = c.text.strip()
                elif t == "link":
                    link = c.get("href") or (c.text or "").strip()
                    if link:
                        break
                elif t == "guid" and c.text and not link:
                    link = c.text.strip()
            if title and link:
                links.append((title, link))
    return ch_title, links


def probe_article(url):
    """Return dict of selector -> (text_len, sample) plus page title selector hint."""
    r = get(url)
    soup = BeautifulSoup(r.text, "html.parser")
    for bad in soup(["script", "style", "noscript", "iframe"]):
        bad.decompose()
    results = {}
    for sel in SELECTOR_CANDIDATES:
        node = soup.select_one(sel)
        if node is None:
            continue
        txt = re.sub(r"\s+", " ", node.get_text(" ", strip=True))
        n = len(txt)
        if n >= 200 and (sel not in results or n > results[sel][0]):
            results[sel] = (n, txt[:120])
    # title hints
    h1 = soup.select_one("h1")
    og = soup.find("meta", property="og:title")
    return {
        "final_url": r.url,
        "h1": h1.get_text(strip=True)[:100] if h1 else None,
        "og_title": (og.get("content") or "")[:100] if og else None,
        "selectors": dict(sorted(results.items(), key=lambda kv: -kv[1][0])),
    }


def main():
    ids = sys.argv[1:] or list(CANDIDATES)
    report = {}
    for sid in ids:
        name, feed, home = CANDIDATES[sid]
        entry = {"name": name, "feed": feed}
        print(f"--- {sid} ({name})")
        try:
            r = get(feed)
            ch, links = feed_links(r.text)
            entry["feed_ok"] = True
            entry["feed_status"] = r.status_code
            entry["items"] = len(links)
            entry["first_titles"] = [t for t, _ in links[:3]]
            if links:
                art_url = links[0][1]
                entry["article_url"] = art_url
                try:
                    entry["article"] = probe_article(art_url)
                    best = next(iter(entry["article"]["selectors"].items()), None)
                    print(f"    feed {len(links)} items; best selector: "
                          f"{best[0] if best else 'NONE'} ({best[1][0] if best else 0} chars)")
                except Exception as e:
                    entry["article_error"] = f"{type(e).__name__}: {e}"
                    print(f"    article fetch failed: {e}")
            else:
                print(f"    feed ok but 0 items parsed (status {r.status_code}, {len(r.text)} bytes)")
        except Exception as e:
            entry["feed_ok"] = False
            entry["error"] = f"{type(e).__name__}: {e}"
            print(f"    FEED FAILED: {e}")
        report[sid] = entry
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nreport -> {OUT}")


if __name__ == "__main__":
    main()
