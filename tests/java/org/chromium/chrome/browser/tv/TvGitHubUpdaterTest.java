package org.chromium.chrome.browser.tv;

public final class TvGitHubUpdaterTest {
    public static void main(String[] args) {
        String json =
                "{\"tag_name\":\"v1.2.3\",\"assets\":["
                        + "{\"name\":\"Tihulu-TV-Browser-arm64.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v1.2.3/Tihulu-TV-Browser-arm64.apk\"},"
                        + "{\"name\":\"Tihulu-TV-Browser-arm.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v1.2.3/Tihulu-TV-Browser-arm.apk\"},"
                        + "{\"name\":\"Tihulu-TV-Browser-x64.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v1.2.3/Tihulu-TV-Browser-x64.apk\"},"
                        + "{\"name\":\"Tihulu-TV-Browser-x86.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v1.2.3/Tihulu-TV-Browser-x86.apk\"}]}";

        TvGitHubUpdater.ReleaseInfo release = TvGitHubUpdater.parseRelease(json);
        assert release != null;
        assert "v1.2.3".equals(release.tag);
        assert release.apks.size() == 4;
        assert "Tihulu-TV-Browser-arm64.apk".equals(release.bestApkFor("aarch64").name);
        assert "Tihulu-TV-Browser-arm.apk".equals(release.bestApkFor("armv7l").name);
        assert "Tihulu-TV-Browser-x64.apk".equals(release.bestApkFor("amd64").name);
        assert "Tihulu-TV-Browser-x86.apk".equals(release.bestApkFor("x86").name);

        String oneApk =
                "{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"browser.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v2/browser.apk\"}]}";
        TvGitHubUpdater.ReleaseInfo fallback = TvGitHubUpdater.parseRelease(oneApk);
        assert fallback != null;
        assert fallback.bestApkFor("unknown") == null;
        assert fallback.bestApkFor("aarch64") == null;
        assert fallback.bestApkFor("armv7l") == null;

        String universalJson =
                "{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"Tihulu-TV-Browser-universal.apk\","
                        + "\"browser_download_url\":\"https://github.com/Tihulu/tihulu-brave-tv/releases/download/v2/Tihulu-TV-Browser-universal.apk\"}]}";
        TvGitHubUpdater.ReleaseInfo universal = TvGitHubUpdater.parseRelease(universalJson);
        assert universal != null;
        assert universal.bestApkFor("aarch64") != null;
        assert universal.bestApkFor("armv7l") != null;
        assert universal.bestApkFor("unknown") != null;

        String untrusted =
                "{\"tag_name\":\"v3\",\"assets\":["
                        + "{\"name\":\"bad.apk\",\"browser_download_url\":\"https://example.com/bad.apk\"},"
                        + "{\"name\":\"other-repo.apk\",\"browser_download_url\":\"https://github.com/other/project/releases/download/v3/other-repo.apk\"}]}";
        TvGitHubUpdater.ReleaseInfo rejected = TvGitHubUpdater.parseRelease(untrusted);
        assert rejected != null;
        assert rejected.apks.isEmpty();
    }
}
