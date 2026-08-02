#!/usr/bin/env python3
"""Connectivity check for recipe candidates — FORCES direct connection (no proxy).

Usage: python check_connectivity.py [proxy|noproxy]
  noproxy (default): ProxyHandler({}) — bypasses system proxy entirely
  proxy: use system/env proxy settings (for phase-2 comparison)

Reads recipes_meta.json, tests English candidates, appends to results_<mode>.jsonl
"""
import json
import os
import socket
import ssl
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

HERE = Path(__file__).parent
META = HERE / "recipes_meta.json"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TIMEOUT = 8
WORKERS = 32
PROXY_URL = os.environ.get("PROXY_URL", "http://127.0.0.1:7890")


def test_url(url, use_proxy):
    handlers = [urllib.request.ProxyHandler({"http": PROXY_URL, "https": PROXY_URL})] \
        if use_proxy else [urllib.request.ProxyHandler({})]
    opener = urllib.request.build_opener(*handlers)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    t0 = time.monotonic()
    try:
        with opener.open(req, timeout=TIMEOUT) as r:
            r.read(65536)  # read a bit, not the whole feed
            return {"status": r.status, "final_url": r.geturl(),
                    "latency_ms": int((time.monotonic() - t0) * 1000), "error": None}
    except urllib.error.HTTPError as e:
        return {"status": e.code, "final_url": url,
                "latency_ms": int((time.monotonic() - t0) * 1000), "error": None}
    except Exception as e:
        reason = type(e).__name__
        if isinstance(e, urllib.error.URLError):
            reason = f"URLError:{getattr(e, 'reason', '')}"[:80]
        return {"status": None, "final_url": url,
                "latency_ms": int((time.monotonic() - t0) * 1000), "error": reason}


def classify(status, error):
    if error:
        return "unreachable"
    if status is None:
        return "unreachable"
    if 200 <= status < 400:
        return "ok"
    if status in (401, 402, 403, 429):
        return "blocked"      # 网络可达,但被反爬/付费墙拒绝
    if status == 404:
        return "not_found"    # 链接本身失效
    return f"http_{status}"


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "noproxy"
    use_proxy = mode == "proxy"
    out_path = HERE / f"results_{mode}.jsonl"
    meta = json.loads(META.read_text(encoding="utf-8"))
    cands = [m for m in meta
             if (m.get("language") or "").startswith("en") or "no_language" in m.get("flags", [])]

    # dedupe test URLs: many recipes of same outlet share a domain, but keep per-recipe granularity
    jobs = []
    for m in cands:
        url = m["feed_urls"][0][1] if m.get("feed_urls") else m.get("homepage")
        if url:
            jobs.append((m["id"], url))

    done_ids = set()
    if out_path.exists():
        for line in out_path.read_text(encoding="utf-8").splitlines():
            try:
                done_ids.add(json.loads(line)["id"])
            except Exception:
                pass
    jobs = [j for j in jobs if j[0] not in done_ids]
    print(f"mode={mode} to_test={len(jobs)} (skipped done={len(done_ids)})")

    socket.setdefaulttimeout(TIMEOUT)
    ctx = ssl.create_default_context()
    ssl._create_default_https_context = lambda: ctx

    with out_path.open("a", encoding="utf-8") as f, ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(test_url, url, use_proxy): rid for rid, url in jobs}
        n = 0
        for fut in as_completed(futs):
            rid = futs[fut]
            url = dict((r, u) for r, u in jobs)[rid]
            try:
                res = fut.result()
            except Exception as e:
                res = {"status": None, "final_url": url, "latency_ms": -1, "error": f"executor:{e}"}
            res["id"] = rid
            res["url"] = url
            res["verdict"] = classify(res["status"], res["error"])
            f.write(json.dumps(res, ensure_ascii=False) + "\n")
            f.flush()
            n += 1
            if n % 40 == 0:
                print(f"  progress {n}/{len(jobs)}")
    print("done ->", out_path)


if __name__ == "__main__":
    main()
