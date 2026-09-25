package org.chromium.components.url_formatter;
import org.chromium.url.GURL;
public class UrlFormatter {
    public static GURL fixupUrl(String value){return new GURL(value);}
    public static String formatUrlForSecurityDisplay(String value){return value;}
}
