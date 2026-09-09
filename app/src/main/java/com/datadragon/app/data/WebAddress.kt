package com.datadragon.app.data

/**
 * Validation and opening rules for a `webpage` field's value.
 *
 * What the user typed is what gets stored — trimmed, but never rewritten to make
 * it look tidier. The scheme is only supplied at the moment the address is
 * opened, so "example.com" stays "example.com" on screen and still opens as
 * `https://example.com`.
 *
 * Only HTTP and HTTPS web addresses are accepted. A valid address needs a normal
 * domain-style host: dot-separated labels ending in a real domain portion, so
 * arbitrary prose is never mistaken for a webpage.
 */
object WebAddress {

    /**
     * A dot-separated host whose final portion is a plausible top-level domain
     * (letters only, at least two of them). Labels are alphanumeric and may
     * contain inner hyphens.
     */
    private val HOST = Regex(
        "^(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$"
    )

    /** True when [input] is a webpage address this app is willing to open. */
    fun isValid(input: String): Boolean = host(input) != null

    /**
     * The address to hand to the browser: [input] trimmed, with `https://`
     * prepended when the user typed no scheme. Returns null when [input] isn't a
     * valid webpage address.
     */
    fun openable(input: String): String? {
        val trimmed = input.trim()
        if (host(trimmed) == null) return null
        return if (hasScheme(trimmed)) trimmed else "https://$trimmed"
    }

    private fun hasScheme(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    /**
     * The host portion of [input], or null when it isn't a usable HTTP/HTTPS
     * address. Anything after the host (path, query, fragment) is ignored for
     * validation; a port is allowed and stripped before the host is checked.
     */
    private fun host(input: String): String? {
        var rest = input.trim()
        if (rest.isEmpty()) return null
        // Whitespace anywhere means this is prose, not an address.
        if (rest.any { it.isWhitespace() }) return null

        val scheme = rest.substringBefore("://", missingDelimiterValue = "")
        if (scheme.isNotEmpty()) {
            // A scheme was typed: only http and https are permitted.
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
                return null
            }
            rest = rest.substringAfter("://")
        } else if (rest.contains(':') && rest.substringBefore(':').none { it == '/' || it == '.' }) {
            // Something like "mailto:someone@example.com" — a scheme we don't open.
            return null
        }

        // Trim everything after the authority.
        rest = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        // Credentials in the authority are not something this app accepts.
        if (rest.contains('@')) return null
        // Drop an explicit port, which must be numeric.
        val portSplit = rest.indexOf(':')
        if (portSplit >= 0) {
            val port = rest.substring(portSplit + 1)
            if (port.isEmpty() || port.any { !it.isDigit() }) return null
            rest = rest.substring(0, portSplit)
        }

        return rest.takeIf { HOST.matches(it) }
    }
}
