#!/usr/bin/env python3
"""Build a minimal valid EPUB 3 demo book from saved article fixtures.

Reuses tools/converter/replay_fixtures.py:parse_article() to extract
(title, text, n_imgs) from two fixture articles, then packs them as a
two-chapter EPUB 3 at app/src/main/assets/demo.epub.

Run from the repo root:
    tools/.venv/Scripts/python tools/epub/make_demo_epub.py
"""
import io
import json
import re
import sys
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
SOURCES_DIR = ROOT / "sources"
OUT = ROOT / "app" / "src" / "main" / "assets" / "demo.epub"

sys.path.insert(0, str(ROOT / "tools" / "converter"))
# replay_fixtures re-wraps sys.stdout as UTF-8 at import time; keep a reference
# to that wrapper alive so its buffer isn't closed by GC.
from replay_fixtures import parse_article  # noqa: E402

_UTF8_STDOUT = sys.stdout  # noqa: F841

CHAPTERS = ["npr", "china_daily"]
MAX_CHARS_PER_PARA = 1200

CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

XHTML_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>{title}</title>
  <meta charset="utf-8"/>
</head>
<body>
  <h1>{title}</h1>
{body}
</body>
</html>
"""

NAV_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>Handypage Demo</title>
  <meta charset="utf-8"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
{items}
    </ol>
  </nav>
</body>
</html>
"""

OPF_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">urn:uuid:{uid}</dc:identifier>
    <dc:title>Handypage Demo</dc:title>
    <dc:language>en</dc:language>
    <dc:creator>Handypage</dc:creator>
    <meta property="dcterms:modified">{modified}</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
{manifest_items}
  </manifest>
  <spine>
{spine_items}
  </spine>
</package>
"""


def paragraphs(text: str) -> list[str]:
    """Split extracted plain text into readable paragraph chunks."""
    sentences = re.split(r"(?<=[.!?])\s+", text)
    paras, buf = [], ""
    for s in sentences:
        if buf and len(buf) + len(s) + 1 > MAX_CHARS_PER_PARA:
            paras.append(buf)
            buf = s
        else:
            buf = f"{buf} {s}".strip() if buf else s
    if buf:
        paras.append(buf)
    return paras or [text]


def main() -> None:
    uid = uuid.uuid4()
    modified = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    chapters = []
    for sid in CHAPTERS:
        cfg = json.loads((SOURCES_DIR / f"{sid}.json").read_text(encoding="utf-8"))
        art_path = SOURCES_DIR / cfg["test"]["fixture"] / "article.html"
        title, text, n_imgs = parse_article(cfg, art_path)
        if not title or not text:
            raise SystemExit(f"[FAIL] {sid}: no title/text extracted from {art_path}")
        chapters.append({"id": sid, "title": title, "text": text, "imgs": n_imgs})

    nav_items = "\n".join(
        f'      <li><a href="ch{i + 1}.xhtml">{escape(c["title"])}</a></li>'
        for i, c in enumerate(chapters)
    )
    manifest_items = "\n".join(
        f'    <item id="ch{i + 1}" href="ch{i + 1}.xhtml" media-type="application/xhtml+xml"/>'
        for i in range(len(chapters))
    )
    spine_items = "\n".join(
        f'    <itemref idref="ch{i + 1}"/>' for i in range(len(chapters))
    )

    files = {
        "META-INF/container.xml": CONTAINER_XML,
        "OEBPS/content.opf": OPF_TEMPLATE.format(
            uid=uid, modified=modified,
            manifest_items=manifest_items, spine_items=spine_items,
        ),
        "OEBPS/nav.xhtml": NAV_TEMPLATE.format(items=nav_items),
    }
    for i, c in enumerate(chapters):
        body = "\n".join(f"  <p>{escape(p)}</p>" for p in paragraphs(c["text"]))
        files[f"OEBPS/ch{i + 1}.xhtml"] = XHTML_TEMPLATE.format(
            title=escape(c["title"]), body=body,
        )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w") as z:
        # mimetype MUST be the first entry and stored uncompressed (EPUB spec)
        z.writestr(
            zipfile.ZipInfo("mimetype", date_time=(1980, 1, 1, 0, 0, 0)),
            "application/epub+zip",
            compress_type=zipfile.ZIP_STORED,
        )
        for name, content in files.items():
            z.writestr(name, content, compress_type=zipfile.ZIP_DEFLATED)

    # self-check
    with zipfile.ZipFile(OUT) as z:
        infos = z.infolist()
        assert infos[0].filename == "mimetype", "mimetype is not the first entry"
        assert infos[0].compress_type == zipfile.ZIP_STORED, "mimetype not STORED"
        names = set(z.namelist())
        expected = {
            "mimetype", "META-INF/container.xml", "OEBPS/content.opf",
            "OEBPS/nav.xhtml", "OEBPS/ch1.xhtml", "OEBPS/ch2.xhtml",
        }
        missing = expected - names
        assert not missing, f"missing entries: {missing}"

    print(f"[ok] wrote {OUT} ({OUT.stat().st_size} bytes)")
    for c in chapters:
        print(f"[ok] {c['id']:14s} chars: {len(c['text']):6d}  imgs: {c['imgs']}  title: {c['title'][:60]}")


if __name__ == "__main__":
    main()
