# -*- coding: utf-8 -*-
"""Reproduce the M3 'sentence explanation hangs' report outside the app.

Pulls the BYOK DeepSeek key from the device via adb (never printed), then
runs two streaming chat/completions requests that mirror Prompts.kt:
  1) explainWord  (known to work on device)
  2) explainSentence (reported to hang at 'generating')
and prints a timeline of SSE events so we can see where stream 2 stalls.
"""
import io
import json
import re
import subprocess
import sys
import time
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ADB = r"C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
BASE = "https://api.deepseek.com"
MODEL = "deepseek-v4-pro"

SYSTEM = (
    "你是一位耐心、专业的英语私教，正在辅导一位阅读英文新闻的中文母语学习者。"
    "始终使用简体中文回答（除非题目要求英文），条理清晰，控制篇幅。"
)


def get_key() -> str:
    out = subprocess.run(
        [ADB, "shell", "run-as dev.handypage.app cat shared_prefs/ai_settings.xml"],
        capture_output=True, text=True, check=True,
    ).stdout
    m = re.search(r'name="key_deepseek">([^<]+)<', out)
    if not m:
        sys.exit("key not found on device")
    return m.group(1)


def stream(name: str, key: str, user: str, timeout_s: int = 180) -> None:
    body = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
        ],
        "stream": True,
    }).encode()
    req = urllib.request.Request(
        BASE + "/chat/completions",
        data=body,
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
        },
    )
    print(f"\n=== {name}: POST {MODEL} stream ===", flush=True)
    t0 = time.monotonic()
    n_reason = n_content = 0
    first_reason = first_content = None
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            print(f"HTTP {resp.status} after {time.monotonic()-t0:.1f}s", flush=True)
            for raw in resp:
                line = raw.decode("utf-8", "replace").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    delta = json.loads(data)["choices"][0].get("delta", {})
                except Exception:
                    continue
                if delta.get("reasoning_content"):
                    n_reason += 1
                    if first_reason is None:
                        first_reason = time.monotonic() - t0
                        print(f"  first reasoning_content at {first_reason:.1f}s", flush=True)
                if delta.get("content"):
                    n_content += 1
                    if first_content is None:
                        first_content = time.monotonic() - t0
                        print(f"  first content at {first_content:.1f}s", flush=True)
    except Exception as e:
        print(f"  EXCEPTION after {time.monotonic()-t0:.1f}s: {e!r}", flush=True)
        return
    dt = time.monotonic() - t0
    print(
        f"  done in {dt:.1f}s: reasoning_deltas={n_reason} content_deltas={n_content} "
        f"first_reasoning={first_reason} first_content={first_content}",
        flush=True,
    )


def main() -> None:
    key = get_key()
    word_user = (
        '请讲解单词 "resilience"，它出现在下面的句子中：\n'
        '"The community showed remarkable resilience after the flood."\n'
        "要求：\n1. 给出该词在这句话中的语境释义（词性 + 中文）；\n"
        "2. 列出 2-3 个常见搭配；\n3. 给出 2 个简短例句，每个例句附中文翻译；\n全文不超过 200 字。"
    )
    # Sentence + surrounding context like onExplainAction() builds; keep the
    # context realistically long (a full paragraph or two).
    sentence = (
        "The measure, which had been delayed for months amid fierce lobbying "
        "from industry groups, was finally approved by the committee on Tuesday."
    )
    context = " ".join(
        "Lawmakers debated the proposal late into the night." for _ in range(40)
    )
    sent_user = (
        f'请讲解下面这个句子/段落：\n"{sentence}"\n'
        f'上下文：\n"{context}"\n'
        "要求：\n1. 语法拆解：指出句子主干和从句结构；\n"
        "2. 标注其中的生词和短语（词性 + 中文释义）；\n3. 给出地道的中文翻译；\n"
        "使用简体中文，条理清晰，控制篇幅。"
    )
    stream("explainWord", key, word_user)
    stream("explainSentence", key, sent_user)


if __name__ == "__main__":
    main()
