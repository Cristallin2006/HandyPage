# Third-Party Notices

Handypage includes code and algorithms derived from the following open-source
projects.

## calibre (source-fetching architecture)

Handypage's built-in source system (`app/src/main/assets/sources/*.json` and
`dev.handypage.app.engine.SourceEngine`) adapts the recipe architecture of
**calibre** — fetch an index page, extract article links, then extract article
bodies with CSS selectors — and its initial source catalog was audited from
calibre's built-in recipe collection. The engine itself is an original Kotlin
implementation for Android; no calibre source code is copied.

calibre is Copyright Kovid Goyal and is licensed under the GNU General Public
License, Version 3 (<https://www.gnu.org/licenses/gpl-3.0.html>). See
<https://github.com/kovidgoyal/calibre> for the source code. In accordance
with the GPL, Handypage as a whole is distributed under GPLv3 (see `LICENSE`).

## Readium Kotlin Toolkit

Handypage uses the **Readium Kotlin Toolkit**
(`org.readium.kotlin-toolkit:readium-streamer`, `readium-navigator`) to parse
and render EPUB publications in the article reader. Readium is licensed under
the BSD 3-Clause License
(<https://github.com/readium/kotlin-toolkit/blob/develop/LICENSE>).

## ECDICT

Handypage bundles a condensed offline build of the **ECDICT** English-Chinese
dictionary (`app/src/main/assets/dict/handypage_dict.db.zip`) for tap-to-lookup
word cards. ECDICT is Copyright 2025 Linwei (skywind3000) and is licensed
under the MIT License; a copy ships as
`app/src/main/assets/dict/ECDICT-LICENSE.txt`. See
<https://github.com/skywind3000/ECDICT>.

## Pinyon Script

Handypage bundles the **Pinyon Script** typeface
(`app/src/main/res/font/pinyon_script.ttf`, subset to the R/L/A/S tab glyphs)
for the editorial bottom-bar letterforms. Pinyon Script is Copyright 2010 The
Pinyon Script Project Authors and is licensed under the SIL Open Font License,
Version 1.1; a copy ships as `app/src/main/assets/pinyon_script/OFL.txt`.

## Bundled libraries

Handypage also builds on: Kotlin and Jetpack Compose / AndroidX (Apache-2.0),
OkHttp (Apache-2.0), jsoup (MIT), Markwon (Apache-2.0), Room (Apache-2.0),
kotlinx.coroutines (Apache-2.0). License texts are available from each
project's repository.

## OpenDataLoader PDF (PDF layout analysis algorithms)

The PDF layout package `dev.handypage.app.pdf` (`XYCut.kt`,
`PdfLayoutParser.kt`) ports and simplifies layout algorithms from
**OpenDataLoader PDF** (`org.opendataloader.pdf`):

- XY-Cut++ reading-order detection
  (`processors/readingorder/XYCutPlusPlusSorter.java`)
- Repeating header/footer removal (`processors/HeaderFooterProcessor.java`)
- Font-size statistics for heading detection (`utils/TextNodeStatistics.java`)

OpenDataLoader PDF is Copyright 2025-2026 Hancom, Inc. and is licensed under
the Apache License, Version 2.0
(<http://www.apache.org/licenses/LICENSE-2.0>). As stated in its NOTICE file,
the product bundles third-party components whose complete copyright and
license information lives in the project's `THIRD_PARTY/` directory; see
<https://github.com/opendataloader-project/opendataloader-pdf> for the
complete source code and notices. The ported code was rewritten in Kotlin and
simplified for arXiv-style single/two-column papers; each ported file carries
an attribution header.

## pdfbox-android

Handypage uses the **pdfbox-android** library
(`com.tom-roush:pdfbox-android`) to extract text and geometry from PDF
documents. pdfbox-android is an Android port of Apache PDFBox maintained by
Tom Roush, licensed under the Apache License, Version 2.0
(<http://www.apache.org/licenses/LICENSE-2.0>). Apache PDFBox is Copyright
The Apache Software Foundation.

## pdf.js

Handypage bundles **pdf.js** (`app/src/main/assets/pdfjs/`: `pdf.mjs`,
`pdf.worker.mjs`, `standard_fonts/`, `cmaps/`) to render arXiv papers in
their original layout inside the paper reader. pdf.js is developed by
Mozilla and licensed under the Apache License, Version 2.0
(<http://www.apache.org/licenses/LICENSE-2.0>); a copy of the license ships
as `app/src/main/assets/pdfjs/LICENSE`. See
<https://github.com/mozilla/pdf.js> for the source code.

## Fraunces

Handypage bundles the **Fraunces** typeface
(`app/src/main/res/font/fraunces_semibold.ttf`, `fraunces_bold.ttf`) for the
editorial display typography of the main interface. Fraunces is Copyright
2018 The Fraunces Project Authors
(<https://github.com/undercasetype/Fraunces>) and is licensed under the SIL
Open Font License, Version 1.1; a copy of the license ships in the APK as
`app/src/main/assets/fraunces/OFL.txt` (the Android resource merger only
accepts font files under `res/font/`, so the license text lives in assets).
