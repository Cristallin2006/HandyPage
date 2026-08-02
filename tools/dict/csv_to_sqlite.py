#!/usr/bin/env python3
"""Convert ECDICT ecdict.csv to the Handypage dictionary SQLite database.

Output schema (two lookup tables, designed for millisecond point-lookup):

  words(word TEXT PRIMARY KEY COLLATE NOCASE,
        phonetic, definition, translation, pos, tag,
        collins INT, bnc INT, frq INT,
        lemma TEXT)          -- exchange "0:" target when the row is itself an inflection

  inflections(form TEXT COLLATE NOCASE, lemma TEXT,
              PRIMARY KEY(form, lemma))  -- built FROM lemma rows' exchange pairs
                                         -- (p/d/i/3/s/r/t values -> owning word)

Lookup chain (app side): lowercase word -> words direct hit (follow `lemma`
for display if set) -> miss? inflections -> lemma's words row.

Usage: python csv_to_sqlite.py [input.csv] [output.db]
"""
import csv
import io
import sqlite3
import sys
import time
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = Path(__file__).parent
SRC = HERE / "ECDICT-master" / "ecdict.csv"
OUT = HERE / "handypage_dict.db"

# exchange keys that carry inflected FORMS (0: is the lemma pointer, 1: is a type code)
INFLECTION_KEYS = {"p", "d", "i", "3", "s", "r", "t"}


def parse_exchange(raw: str):
    """'p:ran/i:running/0:run/1:d' -> {'p':'ran','i':'running','0':'run','1':'d'}"""
    out = {}
    for part in (raw or "").split("/"):
        if ":" in part:
            k, v = part.split(":", 1)
            if v:
                out[k] = v
    return out


def unescape(text: str) -> str:
    # ECDICT stores newlines as literal backslash-n
    return (text or "").replace("\\n", "\n").strip()


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else SRC
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else OUT
    if out.exists():
        out.unlink()

    db = sqlite3.connect(out)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    db.executescript("""
        CREATE TABLE words(
            word TEXT PRIMARY KEY COLLATE NOCASE,
            phonetic TEXT, definition TEXT, translation TEXT,
            pos TEXT, tag TEXT,
            collins INTEGER, bnc INTEGER, frq INTEGER,
            lemma TEXT
        );
        CREATE TABLE inflections(
            form TEXT COLLATE NOCASE,
            lemma TEXT,
            PRIMARY KEY(form, lemma)
        ) WITHOUT ROWID;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    """)

    t0 = time.monotonic()
    n_words = 0
    inflections = set()

    with open(src, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        batch = []
        for row in reader:
            exch = parse_exchange(row["exchange"])
            lemma = exch.get("0")
            batch.append((
                row["word"], row["phonetic"], unescape(row["definition"]),
                unescape(row["translation"]), row["pos"], row["tag"],
                int(row["collins"] or 0), int(row["bnc"] or 0), int(row["frq"] or 0),
                lemma if lemma and lemma.lower() != row["word"].lower() else None,
            ))
            # this row's own inflected forms -> this row is their lemma entry
            base = lemma or row["word"]
            for k, v in exch.items():
                if k in INFLECTION_KEYS and v.lower() not in (row["word"].lower(), base.lower()):
                    inflections.add((v, base))
            n_words += 1
            if len(batch) >= 5000:
                db.executemany("INSERT OR REPLACE INTO words VALUES (?,?,?,?,?,?,?,?,?,?)", batch)
                batch.clear()
        if batch:
            db.executemany("INSERT OR REPLACE INTO words VALUES (?,?,?,?,?,?,?,?,?,?)", batch)

    print(f"words inserted: {n_words} ({time.monotonic()-t0:.1f}s)")

    # drop inflection targets that don't exist in words (dangling) is unnecessary
    # for lookup correctness — a miss just falls through. Insert as-is.
    db.executemany("INSERT OR IGNORE INTO inflections VALUES (?,?)", inflections)
    print(f"inflections: {len(inflections)}")

    db.execute("CREATE INDEX idx_inflections_form ON inflections(form)")
    db.executemany("INSERT INTO meta VALUES (?,?)", [
        ("source", "ECDICT (https://github.com/skywind3000/ECDICT)"),
        ("license", "MIT"),
        ("built_at", time.strftime("%Y-%m-%d")),
        ("schema", "1"),
    ])
    db.commit()
    db.execute("VACUUM")
    db.execute("ANALYZE")
    db.commit()
    size_mb = out.stat().st_size / 1e6
    print(f"db size: {size_mb:.1f} MB")

    # smoke checks: the lookup chain must resolve these
    checks = {"running": "run", "went": "go", "children": "child", "studies": "study", "bigger": "big"}
    for form, expect in checks.items():
        hit = db.execute("SELECT word, lemma FROM words WHERE word=?", (form,)).fetchone()
        via = db.execute("SELECT lemma FROM inflections WHERE form=?", (form,)).fetchall()
        print(f"  check {form:10s} direct={hit} inflections={via} (expect ~{expect})")
    db.close()


if __name__ == "__main__":
    main()
