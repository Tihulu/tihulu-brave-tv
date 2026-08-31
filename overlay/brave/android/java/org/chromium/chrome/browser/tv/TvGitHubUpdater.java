/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads the newest packaged TV APK from GitHub Releases and hands it to Android. */
final class TvGitHubUpdater {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Tihulu/tihulu-brave-tv/releases/latest";
    private static final String TRUSTED_RELEASE_PREFIX =
            "https://github.com/Tihulu/tihulu-brave-tv/releases/download/";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final long POLL_TIMEOUT_MS = 2L * 60L * 60L * 1000L;
    private static final AtomicBoolean UPDATE_RUNNING = new AtomicBoolean(false);

    private TvGitHubUpdater() {}

    static void checkAndInstall(Context context, View uiAnchor) {
        if (context == null || uiAnchor == null) return;
        if (!UPDATE_RUNNING.compareAndSet(false, true)) {
            toast(context, uiAnchor, "An update check or download is already running.");
            return;
        }
        toast(context, uiAnchor, "Checking GitHub for updates...");
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                ReleaseInfo release = fetchLatestRelease();
                                if (release == null) {
                                    toast(
                                            context,
                                            uiAnchor,
                                            "No packaged GitHub release is available yet.");
                                    return;
                                }

                                Asset apk = release.bestApkFor(System.getProperty("os.arch", ""));
                                if (apk == null) {
                                    toast(
                                            context,
                                            uiAnchor,
                                            "Latest release has no compatible APK asset.");
                                    return;
                                }

                                DownloadManager manager =
                                        (DownloadManager)
                                                context.getSystemService(Context.DOWNLOAD_SERVICE);
                                if (manager == null) {
                                    toast(context, uiAnchor, "Android Download Manager is unavailable.");
                                    return;
                                }

                                String tag = release.tag.isEmpty() ? "latest" : release.tag;
                                String fileName =
                                        "Tihulu-TV-Browser-"
                                                + tag.replaceAll("[^A-Za-z0-9._-]", "_")
                                                + "-"
                                                + System.currentTimeMillis()
                                                + ".apk";
                                DownloadManager.Request request =
                                        new DownloadManager.Request(Uri.parse(apk.url))
                                                .setTitle("Tihulu TV Browser " + tag)
                                                .setDescription("Downloading update from GitHub")
                                                .setMimeType(APK_MIME)
                                                .setNotificationVisibility(
                                                        DownloadManager.Request
                                                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalFilesDir(
                                                        context,
                                                        Environment.DIRECTORY_DOWNLOADS,
                                                        fileName);
                                long downloadId = manager.enqueue(request);
                                toast(context, uiAnchor, "Downloading " + tag + "...");
                                waitForDownload(context, uiAnchor, manager, downloadId, tag);
                            } catch (IOException error) {
                                toast(
                                        context,
                                        uiAnchor,
                                        "Update check failed: " + safeMessage(error));
                            } catch (RuntimeException error) {
                                toast(
                                        context,
                                        uiAnchor,
                                        "Update failed: " + safeMessage(error));
                            } finally {
                                UPDATE_RUNNING.set(false);
                            }
                        },
                        "tihulu-tv-updater");
        worker.start();
    }

    private static ReleaseInfo fetchLatestRelease() throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "Tihulu-TV-Browser-Updater");
        connection.setInstanceFollowRedirects(true);

        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
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
            return parseRelease(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    static ReleaseInfo parseRelease(String json) {
        if (json == null || json.isEmpty()) return null;
        String tag = stringValueForKey(json, "tag_name", 0);
        List<Asset> apks = new ArrayList<>();
        int searchFrom = 0;
        String urlKey = "\"browser_download_url\"";
        while (true) {
            int urlIndex = json.indexOf(urlKey, searchFrom);
            if (urlIndex < 0) break;
            int nameIndex = json.lastIndexOf("\"name\"", urlIndex);
            String name = nameIndex < 0 ? "" : stringValueAtKeyIndex(json, nameIndex);
            String url = stringValueAtKeyIndex(json, urlIndex);
            if (name.toLowerCase(Locale.ROOT).endsWith(".apk")
                    && url.startsWith(TRUSTED_RELEASE_PREFIX)) {
                apks.add(new Asset(name, url));
            }
            searchFrom = urlIndex + urlKey.length();
        }
        return new ReleaseInfo(tag, apks);
    }

    private static void waitForDownload(
            Context context,
            View uiAnchor,
            DownloadManager manager,
            long downloadId,
            String tag) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Cursor cursor = null;
            try {
                cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId));
                if (cursor != null && cursor.moveToFirst()) {
                    int status =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Uri apkUri = manager.getUriForDownloadedFile(downloadId);
                        if (apkUri == null) {
                            toast(context, uiAnchor, "Update downloaded, but APK URI is unavailable.");
                            return;
                        }
                        launchInstaller(context, uiAnchor, apkUri, tag);
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        toast(context, uiAnchor, "GitHub APK download failed.");
                        return;
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }

            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        toast(
                context,
                uiAnchor,
                "Download is still running. Use the Android download notification when it finishes.");
    }

    private static void launchInstaller(Context context, View uiAnchor, Uri apkUri, String tag) {
        uiAnchor.post(
                () -> {
                    try {
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(apkUri, APK_MIME);
                        install.addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(install);
                        Toast.makeText(
                                        context,
                                        "Downloaded "
                                                + tag
                                                + ". If Android asks, allow this app to install unknown apps.",
                                        Toast.LENGTH_LONG)
                                .show();
                    } catch (RuntimeException error) {
                        Toast.makeText(
                                        context,
                                        "APK downloaded but installer could not open: "
                                                + safeMessage(error),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    private static void toast(Context context, View uiAnchor, String message) {
        uiAnchor.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String stringValueForKey(String json, String key, int fromIndex) {
        int keyIndex = json.indexOf("\"" + key + "\"", fromIndex);
        return keyIndex < 0 ? "" : stringValueAtKeyIndex(json, keyIndex);
    }

    private static String stringValueAtKeyIndex(String json, int keyIndex) {
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) return "";
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return "";
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            try {
                                value.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException invalidUnicode) {
                                value.append('u');
                            }
                        }
                        break;
                    default: value.append(ch); break;
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return value.toString();
            } else {
                value.append(ch);
            }
        }
        return "";
    }

    static final class ReleaseInfo {
        final String tag;
        final List<Asset> apks;

        ReleaseInfo(String tag, List<Asset> apks) {
            this.tag = tag == null ? "" : tag;
            this.apks = apks;
        }

        Asset bestApkFor(String architecture) {
            String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
            String[] suffixes;
            String[] aliases;
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                suffixes = new String[] {"-arm64.apk", "_arm64.apk", "-aarch64.apk", "_aarch64.apk"};
                aliases = new String[] {"arm64-v8a"};
            } else if (arch.startsWith("arm")) {
                suffixes = new String[] {"-arm.apk", "_arm.apk", "-arm32.apk", "_arm32.apk"};
                aliases = new String[] {"armeabi", "armv7"};
            } else if (arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64")) {
                suffixes = new String[] {"-x64.apk", "_x64.apk", "-x86_64.apk", "_x86_64.apk"};
                aliases = new String[] {"x86-64"};
            } else if (arch.equals("x86") || arch.matches("i[3-6]86")) {
                suffixes = new String[] {"-x86.apk", "_x86.apk"};
                aliases = new String[] {"x86-32"};
            } else {
                suffixes = new String[0];
                aliases = new String[0];
            }

            for (Asset asset : apks) {
                String name = asset.name.toLowerCase(Locale.ROOT);
                for (String suffix : suffixes) {
                    if (name.endsWith(suffix)) return asset;
                }
            }
            for (Asset asset : apks) {
                String name = asset.name.toLowerCase(Locale.ROOT);
                for (String alias : aliases) {
                    if (name.contains(alias)) return asset;
                }
            }
            for (Asset asset : apks) {
                if (asset.name.toLowerCase(Locale.ROOT).contains("universal")) return asset;
            }
            return null;
        }
    }

    static final class Asset {
        final String name;
        final String url;

        Asset(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
