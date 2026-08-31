/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.chromium.net.ChromiumNetworkAdapter;
import org.chromium.net.NetworkTrafficAnnotationTag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Read-only Brave upstream checker. It never replaces browser-engine files in place. */
final class TvBraveUpstream {
    private static final String LATEST_STABLE_API =
            "https://api.github.com/repos/brave/brave-browser/releases/latest";
    private static final NetworkTrafficAnnotationTag TRAFFIC_ANNOTATION =
            NetworkTrafficAnnotationTag.createComplete(
                    "tihulu_tv_brave_upstream_check",
                    """
                    semantics {
                      sender: "Tihulu TV Browser Brave upstream checker"
                      description:
                        "Checks GitHub's public Brave release metadata when the user explicitly "
                        "requests an upstream version check."
                      trigger: "User selects Check Brave upstream in Tihulu TV Browser."
                      data: "No browsing or application data; only standard network metadata."
                      destination: WEBSITE
                      user_data { type: NONE }
                    }
                    policy {
                      cookies_allowed: NO
                      setting: "The request only occurs after explicit user action."
                      policy_exception_justification: "Not applicable."
                    }""");

    private TvBraveUpstream() {}

    static void check(Context context, View uiAnchor) {
        if (context == null || uiAnchor == null) return;
        toast(context, uiAnchor, "Checking Brave upstream...");
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                String latest = fetchLatestStableTag();
                                if (latest.isEmpty()) {
                                    toast(context, uiAnchor, "Brave upstream version is unavailable.");
                                    return;
                                }
                                String local = TvBuildInfo.BRAVE_VERSION;
                                int comparison = compareVersions(local, latest);
                                if (comparison < 0) {
                                    toast(
                                            context,
                                            uiAnchor,
                                            "Brave "
                                                    + stripLeadingV(latest)
                                                    + " is available upstream. This APK uses Brave "
                                                    + local
                                                    + ". Update safely through a newer Tihulu TV Browser APK.");
                                } else if (comparison == 0) {
                                    toast(
                                            context,
                                            uiAnchor,
                                            "Brave engine is current with public stable: "
                                                    + stripLeadingV(latest)
                                                    + ".");
                                } else {
                                    toast(
                                            context,
                                            uiAnchor,
                                            "This APK uses Brave "
                                                    + local
                                                    + "; public stable is "
                                                    + stripLeadingV(latest)
                                                    + ". This build may be newer/development.");
                                }
                            } catch (IOException error) {
                                toast(
                                        context,
                                        uiAnchor,
                                        "Brave upstream check failed: " + safeMessage(error));
                            } catch (RuntimeException error) {
                                toast(
                                        context,
                                        uiAnchor,
                                        "Brave upstream check failed: " + safeMessage(error));
                            }
                        },
                        "tihulu-brave-upstream");
        worker.start();
    }

    private static String fetchLatestStableTag() throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection)
                        ChromiumNetworkAdapter.openConnection(
                                new URL(LATEST_STABLE_API), TRAFFIC_ANNOTATION);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Tihulu-TV-Browser-Brave-Checker");
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub HTTP " + status);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
            }
            return parseTag(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    static String parseTag(String json) {
        if (json == null) return "";
        String key = "\"tag_name\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) return "";
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) return "";
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return "";
        int end = json.indexOf('"', start + 1);
        if (end < 0) return "";
        return json.substring(start + 1, end);
    }

    static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        String normalized = stripLeadingV(value == null ? "" : value.trim());
        String[] raw = normalized.split("\\.");
        int[] result = new int[Math.min(raw.length, 4)];
        for (int i = 0; i < result.length; i++) {
            String token = raw[i].replaceFirst("[^0-9].*$", "");
            if (token.isEmpty()) {
                result[i] = 0;
            } else {
                try {
                    result[i] = Integer.parseInt(token);
                } catch (NumberFormatException invalid) {
                    result[i] = 0;
                }
            }
        }
        return result;
    }

    private static String stripLeadingV(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.charAt(0) == 'v' || value.charAt(0) == 'V' ? value.substring(1) : value;
    }

    private static void toast(Context context, View uiAnchor, String message) {
        uiAnchor.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
