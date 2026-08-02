package dev.handypage.app

import java.net.Inet6Address
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * M18: try IPv4 before IPv6. OkHttp attempts addresses in DNS order and has
 * no happy-eyeballs; on networks with broken IPv6 (common on CN carriers) an
 * AAAA-first host (e.g. apod.nasa.gov) burns the 10s connect timeout on a
 * blackholed SYN and then the call timeout fires during the v4 retry — the
 * fetch fails even though plain IPv4 works fine. v4-first makes the working
 * route win immediately; v6-only hosts still fall through to v6 after v4
 * fails. Applied to every OkHttpClient the app builds (engine, AI, arXiv).
 */
val ipv4FirstDns = Dns { hostname ->
    Dns.SYSTEM.lookup(hostname).sortedBy { it is Inet6Address }
}

/** Fresh client with [ipv4FirstDns]; share per-owner as before. */
fun handypageHttpClient(): OkHttpClient =
    OkHttpClient.Builder().dns(ipv4FirstDns).build()
