/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import java.util.Locale;
import java.util.regex.Pattern;

/** Classify remote input; Chromium still owns URL canonicalization and search. */
final class TvAddressInput {
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*", Pattern.DOTALL);
    private static final Pattern LOCAL_PORT = Pattern.compile("^[a-zA-Z0-9.-]+:[0-9]+(?:[/?#].*)?$");

    private TvAddressInput() {}

    /** Empty means a search query; dangerous/external schemes are rejected, never executed. */
    static String urlCandidate(String value) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("Enter a web address or search term.");
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return input;
        if (SCHEME.matcher(input).matches() && !LOCAL_PORT.matcher(input).matches()) {
            if (input.contains("://") || lower.startsWith("javascript:")
                    || lower.startsWith("data:") || lower.startsWith("file:")
                    || lower.startsWith("intent:") || lower.startsWith("content:")) {
                throw new IllegalArgumentException("Use an http:// or https:// web address.");
            }
            // Search operators such as site:example.com and ordinary colon-separated
            // phrases are queries, not external intents or web hostnames.
            return "";
        }
        for (int i = 0; i < input.length(); i++) {
            if (Character.isWhitespace(input.charAt(i))) return "";
        }
        // A single word remains a search. Explicit http:// also supports local hostnames.
        String authority = input.split("[/?#]", 2)[0];
        if (authority.contains("@")) return "";
        if (authority.contains(".") || authority.startsWith("[")
                || authority.equalsIgnoreCase("localhost") || LOCAL_PORT.matcher(input).matches()) {
            return "https://" + input;
        }
        return "";
    }
}
