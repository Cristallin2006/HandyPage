#!/usr/bin/env python3
"""Generate docs/source-audit.md from recipes_meta.json + results_noproxy.jsonl +
results_proxy.jsonl (two-phase comparison)."""
import json
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
OUT = ROOT / "docs" / "source-audit.md"


def load(mode):
    p = HERE / f"results_{mode}.jsonl"
    if not p.exists():
        return {}
    res = {}
    for line in p.read_text(encoding="utf-8").splitlines():
        try:
            r = json.loads(line)
            res[r["id"]] = r
        except Exception:
            pass
    return res


VERDICT_LABEL = {
    "ok": "✅ 可用",
    "blocked": "⚠️ 可达被拒",
    "not_found": "❌ 404",
    "unreachable": "❌ 不可达",
    "未测试": "➖ 未测试",
}
KIND_LABEL = {"rss": "RSS(低维护)", "parse_index": "自定义解析(高维护)", "unknown": "未知"}
FLAG_LABEL = {"webengine": "需浏览器渲染", "needs_subscription": "需订阅", "from_web": ""}

# 学习者友好度人工精选(基于内容定位,与连通性无关;连通性由测试数据说话)
# 注:VOA Learning English / Breaking News English / News in Levels 并非 calibre 内置
# recipe,需后期自建 source.json,不在本报告统计范围内
LEARNER_PICKS = {
    "npr",               # 美国公共电台,语速慢、用词规范,学习者首选
    "nasa", "apod",      # 科普内容,词汇难度适中
    "korea_herald",      # 亚洲语境英文报纸,直连延迟极低
    "rte",               # 爱尔兰国家媒体
    "moscowtimes_en",    # 英文独立媒体
    "daily_mirror",      # 英国小报,用词简单直接
    "standardmedia_ke",  # 肯尼亚英文媒体
    "mail_and_guardian", # 南非英文媒体
    "hindu_post",        # 印度英文媒体
    "cnetnews", "endgadget",  # 科技新闻,兴趣驱动阅读
    "freenature",        # Nature 新闻 RSS
    "foxnews", "latimes", "irish_times_free", "fortune_magazine",
    "bbc", "bbc_fast", "cnn", "guardian", "al_jazeera",  # 大刊,代理阶段重点
}

BIG_NAMES = {"bbc", "bbc_fast", "cnn", "guardian", "al_jazeera", "reuters",
             "financial_times", "economist_free", "nytimes", "scmp", "japan_times",
             "independent", "telegraph", "washington_post", "abc_au", "cbc_canada",
             "straitstimes", "hindufeeds", "dw", "france24"}


def main():
    meta = json.loads((HERE / "recipes_meta.json").read_text(encoding="utf-8"))
    np_res = load("noproxy")
    px_res = load("proxy")
    has_proxy = bool(px_res)

    en = [m for m in meta if (m.get("language") or "").startswith("en") or "no_language" in m.get("flags", [])]
    untestable = [m for m in en if not m.get("feed_urls") and not m.get("homepage")]

    def v_of(res, m):
        return res.get(m["id"], {}).get("verdict", "未测试")

    np_counts = Counter(v_of(np_res, m) for m in en)
    px_counts = Counter(v_of(px_res, m) for m in en) if has_proxy else {}

    revived = [m for m in en
               if v_of(np_res, m) in ("unreachable", "blocked", "not_found") and v_of(px_res, m) == "ok"] \
        if has_proxy else []
    revived_big = [m for m in revived if m["id"] in BIG_NAMES]

    def label(v):
        return VERDICT_LABEL.get(v, v)

    def row(m):
        r_np, r_px = np_res.get(m["id"], {}), px_res.get(m["id"], {})
        v_np, v_px = r_np.get("verdict", "未测试"), (r_px.get("verdict", "未测试") if has_proxy else "-")
        lat = f"{r_np['latency_ms']}ms" if r_np.get("latency_ms") and v_np == "ok" else \
              (f"{r_px['latency_ms']}ms*" if has_proxy and r_px.get("latency_ms") and v_px == "ok" else "-")
        flags = "、".join(filter(None, (FLAG_LABEL.get(f, f) for f in m.get("flags", []) if FLAG_LABEL.get(f, f))))
        url = m["feed_urls"][0][1] if m.get("feed_urls") else (m.get("homepage") or "-")
        return (f"| `{m['id']}` | {m['title']} | {m.get('language') or '?'} | {KIND_LABEL.get(m['kind'], m['kind'])} "
                f"| {label(v_np)} | {label(v_px)} | {lat} | {flags or '-'} | {url} |")

    order = {"ok": 0, "blocked": 1, "not_found": 2, "unreachable": 3, "未测试": 4, "-": 5}
    en_sorted = sorted(en, key=lambda m: (order.get(v_of(np_res, m), 9), order.get(v_of(px_res, m), 9) if has_proxy else 0, m["kind"] != "rss", m["id"]))

    L = []
    A = L.append
    A("# 源验证报告(calibre recipes)")
    A("")
    A(f"- 生成日期:{date.today().isoformat()}")
    A(f"- 候选范围:calibre 内置 1097 个 recipes 中的英文期刊类源,共 **{len(en)}** 个")
    A("- 测试方法:对每个源的主链接(RSS feed 或首页)发起 HTTP GET,浏览器 UA,超时 8s")
    A("- **无代理口径:强制直连(ProxyHandler 置空),不受系统代理影响**")
    if has_proxy:
        A("- **代理口径:HTTP 代理 127.0.0.1:7890(FlClash);明细表中延迟带 * 者为代理下测得**")
    A("- 判定:✅ 2xx/3xx;⚠️ 401/403/429=可达但被反爬或付费墙拒绝;❌ 超时/重置/DNS=不可达")
    A("")

    A("## 一、两阶段连通性对比")
    A("")
    A("| 判定 | 无代理 | 代理 |" if has_proxy else "| 判定 | 无代理 |")
    A("|---|---|---|" if has_proxy else "|---|---|")
    for v in ("ok", "blocked", "not_found", "unreachable", "未测试"):
        if np_counts.get(v) or px_counts.get(v):
            A(f"| {label(v)} | {np_counts.get(v, 0)} | {px_counts.get(v, 0)} |" if has_proxy
              else f"| {label(v)} | {np_counts.get(v, 0)} |")
    if has_proxy:
        A(f"| **代理复活(不可达/被拒→可用)** | - | **{len(revived)}** |")
    A(f"| 无法静态提取 URL(未测) | {len(untestable)} | |" if has_proxy else f"| 无法静态提取 URL(未测) | {len(untestable)} |")
    A("")

    if has_proxy:
        A(f"## 二、代理复活源清单(共 {len(revived)} 个,⭐=学习者推荐)")
        A("")
        A("| 源 ID | 名称 | 类型 | 代理延迟 | 主链接 |")
        A("|---|---|---|---|---|")
        for m in sorted(revived, key=lambda m: (m["id"] not in BIG_NAMES and m["id"] not in LEARNER_PICKS, m["id"])):
            r = px_res[m["id"]]
            star = " ⭐" if m["id"] in LEARNER_PICKS or m["id"] in BIG_NAMES else ""
            A(f"| `{m['id']}` | {m['title']}{star} | {KIND_LABEL.get(m['kind'], m['kind'])} | {r['latency_ms']}ms | "
              f"{m['feed_urls'][0][1] if m.get('feed_urls') else m.get('homepage')} |")
        A("")

    ok_rss = [m for m in en if v_of(np_res, m) == "ok" and m["kind"] == "rss"]
    sec = "三" if has_proxy else "二"
    A(f"## {sec}、无代理直连可用 + RSS 低维护(共 {len(ok_rss)} 个,⭐=学习者推荐)")
    A("")
    A("| 源 ID | 名称 | 延迟 | 主链接 |")
    A("|---|---|---|---|")
    for m in ok_rss:
        r = np_res[m["id"]]
        star = " ⭐" if m["id"] in LEARNER_PICKS else ""
        A(f"| `{m['id']}` | {m['title']}{star} | {r['latency_ms']}ms | {m['feed_urls'][0][1]} |")
    A("")
    A("> ⭐ = 学习者友好推荐(内容定位适合英语学习,人工标注)")
    A("")

    sec = "四" if has_proxy else "三"
    A(f"## {sec}、全部英文候选源明细(按无代理可用性排序)")
    A("")
    if has_proxy:
        A("| 源 ID | 名称 | 语言 | 类型 | 无代理 | 代理 | 延迟 | 备注 | 测试链接 |")
        A("|---|---|---|---|---|---|---|---|---|")
    else:
        A("| 源 ID | 名称 | 语言 | 类型 | 无代理 | 延迟 | 备注 | 测试链接 |")
        A("|---|---|---|---|---|---|---|---|")
    for m in en_sorted:
        A(row(m))

    A("")
    A("## 附录 A:无法静态提取 URL 的源(多为动态 parse_index,需人工)")
    A("")
    A("、".join(f"`{m['id']}`" for m in untestable) or "无")

    A("")
    A("## 附录 B:全部 1097 个源按语言分组")
    A("")
    by_lang = defaultdict(list)
    for m in meta:
        by_lang[(m.get("language") or "?").split("_")[0]].append(m["id"])
    for lang in sorted(by_lang, key=lambda l: -len(by_lang[l])):
        ids = sorted(by_lang[lang])
        A(f"### {lang}({len(ids)} 个)")
        A("")
        A("、".join(f"`{i}`" for i in ids))
        A("")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(L), encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")
    print("noproxy:", dict(np_counts))
    if has_proxy:
        print("proxy:", dict(px_counts), "revived:", len(revived))


if __name__ == "__main__":
    main()
