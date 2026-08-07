/*
 * Handypage bilingual reader — injected into the cached ar5iv/arXiv-HTML
 * page (PaperHtmlRewriter). Talks to the Kotlin `Handypage` bridge:
 *
 *   JS -> Kotlin:  onParagraphs(json)   chunked [{i,text}] paragraph payload
 *                  onSelection / onCleared / onTap  (shared reader chrome)
 *   Kotlin -> JS:  window.__applyTranslations(json)  [{i,t}]
 *                  window.__setBilingualMode("orig"|"target"|"both")
 *
 * Paragraph selection mirrors ar5iv semantics: body paragraphs, section
 * titles and captions are translated; formulas stay MathML — their
 * `alttext` travels inside $...$ so the model keeps them verbatim.
 */
(function () {
  'use strict';

  var CHUNK = 50; // paragraphs per bridge call (binder string safety margin)
  var MIN_CHARS = 3; // skip "OK.", equation-only stubs, stray numbers

  /** Element text with <math> swapped for its LaTeX alttext ($...$). */
  function translatableText(el) {
    var clone = el.cloneNode(true);
    var maths = clone.querySelectorAll('math');
    for (var m = 0; m < maths.length; m++) {
      var alt = maths[m].getAttribute('alttext') || '';
      maths[m].parentNode.replaceChild(
        document.createTextNode(alt ? '$' + alt + '$' : ''),
        maths[m]
      );
    }
    return (clone.textContent || '').replace(/\s+/g, ' ').trim();
  }

  function excluded(el) {
    if (el.classList.contains('ltx_title_document')) return true; // title stays original
    return !!el.closest('.ltx_bibliography, .ltx_authors, .ltx_keywords, .ltx_pagination');
  }

  /** Collects translatable paragraphs and uploads them chunk by chunk. */
  function collect() {
    var root = document.querySelector('article.ltx_document')
      || document.querySelector('main')
      || document.body;
    var nodes = root.querySelectorAll(
      'p.ltx_p, h2.ltx_title, h3.ltx_title, h4.ltx_title, .ltx_caption, .ltx_abstract p'
    );
    var paras = [];
    var seen = new Set();
    for (var n = 0; n < nodes.length; n++) {
      var el = nodes[n];
      if (seen.has(el) || excluded(el)) continue;
      seen.add(el);
      var text = translatableText(el);
      if (!text || text.length < MIN_CHARS) continue;
      var i = paras.length;
      el.setAttribute('data-hp-p', String(i));
      paras.push({ i: i, text: text });
    }
    for (var s = 0; s < paras.length; s += CHUNK) {
      try {
        Handypage.onParagraphs(JSON.stringify(paras.slice(s, s + CHUNK)));
      } catch (e) { /* bridge missing (plain browser preview) — ignore */ }
    }
    // M34: tell Kotlin the paragraph set is COMPLETE — starting translation
    // on the first chunk used to drop every later chunk (job already active).
    try { Handypage.onParagraphsDone(paras.length); } catch (e) { /* ignore */ }
  }

  /**
   * Idempotent translation application: inserts/updates a `.hp-t` block
   * right after its original paragraph, addressed by data-hp-i.
   * Accepts BOTH a JSON string and an already-parsed array — Kotlin calls
   * this via evaluateJavascript with an inline array literal, so a blind
   * JSON.parse would silently no-op on "[object Object]" (M34 bug: nothing
   * ever rendered).
   */
  window.__applyTranslations = function (json) {
    var arr;
    try { arr = (typeof json === 'string') ? JSON.parse(json) : json; } catch (e) { return; }
    if (!arr || !arr.length) return;
    for (var k = 0; k < arr.length; k++) {
      var t = arr[k];
      var el = document.querySelector('[data-hp-p="' + t.i + '"]');
      if (!el) continue;
      var box = document.querySelector('.hp-t[data-hp-i="' + t.i + '"]');
      if (!box) {
        box = document.createElement('div');
        box.className = 'hp-t';
        box.setAttribute('data-hp-i', String(t.i));
        el.parentNode.insertBefore(box, el.nextSibling);
      }
      box.textContent = t.t;
      renderFormulas(box, el);
    }
  };

  /**
   * M36: renders $...$ segments of a translation as REAL math by cloning the
   * matching <math> element out of the original paragraph (matched by
   * alttext, whitespace-normalised). The translator is instructed to keep
   * $...$ verbatim, so the alttext almost always matches; unmatched spans
   * (currency like $0.07, model-added formulas) stay plain text — never
   * worse than before.
   */
  function renderFormulas(box, origEl) {
    var maths = origEl.querySelectorAll('math');
    if (!maths.length) return;
    var byAlt = {};
    for (var m = 0; m < maths.length; m++) {
      var alt = (maths[m].getAttribute('alttext') || '').replace(/\s+/g, ' ').trim();
      if (alt && !byAlt[alt]) byAlt[alt] = maths[m];
    }
    var walker = document.createTreeWalker(box, NodeFilter.SHOW_TEXT);
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    var re = /\$([^$]+)\$/g;
    for (var n = 0; n < nodes.length; n++) {
      var node = nodes[n];
      var text = node.nodeValue;
      re.lastIndex = 0;
      var m2, last = 0, frag = null, replaced = 0;
      while ((m2 = re.exec(text)) !== null) {
        var key = m2[1].replace(/\s+/g, ' ').trim();
        var src = byAlt[key];
        if (!src) continue; // stays raw; the next replaced span re-includes it
        if (!frag) frag = document.createDocumentFragment();
        if (m2.index > last) frag.append(document.createTextNode(text.slice(last, m2.index)));
        frag.append(src.cloneNode(true));
        last = m2.index + m2[0].length;
        replaced++;
      }
      if (frag && replaced > 0) {
        if (last < text.length) frag.append(document.createTextNode(text.slice(last)));
        node.parentNode.replaceChild(frag, node);
      }
    }
  };

  /** orig = originals only, target = translations only, both = side by side. */
  window.__setBilingualMode = function (mode) {
    var html = document.documentElement;
    html.classList.remove('hp-mode-orig', 'hp-mode-target', 'hp-mode-both');
    html.classList.add('hp-mode-' + mode);
  };

  // ------- shared reader chrome: selection reporting + tap toggle -------

  var selTimer = null;
  document.addEventListener('selectionchange', function () {
    if (selTimer) return;
    selTimer = setTimeout(function () {
      selTimer = null;
      var sel = window.getSelection();
      var text = sel && !sel.isCollapsed ? String(sel.toString()).trim() : '';
      try {
        if (text) Handypage.onSelection(text.slice(0, 2000));
        else Handypage.onCleared();
      } catch (e) { /* ignore */ }
    }, 120);
  });

  window.__clearSelection = function () {
    var sel = window.getSelection();
    if (sel) sel.removeAllRanges();
    try { Handypage.onCleared(); } catch (e) { /* ignore */ }
  };

  // Kotlin re-pushes the vocab term set after every page load; the HTML
  // view has no inline highlighting yet, so keep the symbol defined.
  window.__setVocabTerms = window.__setVocabTerms || function () {};

  // A clean single tap toggles the overlay top bar, same as the PDF view.
  document.addEventListener('click', function (e) {
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) return; // a selection gesture, not a tap
    var target = e.target;
    if (target && target.closest && target.closest('a, button, .hp-t')) return;
    try { Handypage.onTap(); } catch (err) { /* ignore */ }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', collect);
  } else {
    collect();
  }
})();
