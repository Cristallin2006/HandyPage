package dev.handypage.app.paper

/**
 * M17 paper-reader top-bar kicker: "arXiv · cs.CL · 2024" — source, primary
 * category, and the 4-digit year from the arXiv published stamp, joined with
 * " · ". Blank pieces drop out; a published stamp without a 4-digit year
 * prefix drops the year. (Uppercasing happens at the call site, same as the
 * EPUB reader's kicker in ReaderShell.)
 */
fun paperKicker(category: String, published: String): String {
    val parts = mutableListOf("arXiv")
    val cat = category.trim()
    if (cat.isNotEmpty()) parts += cat
    val year = published.trim().take(4)
    if (year.length == 4 && year.all(Char::isDigit)) parts += year
    return parts.joinToString(" · ")
}
