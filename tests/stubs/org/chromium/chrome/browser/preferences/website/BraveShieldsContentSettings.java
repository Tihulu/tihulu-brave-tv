package org.chromium.chrome.browser.preferences.website;
import org.chromium.chrome.browser.profiles.Profile;
public class BraveShieldsContentSettings {
 public static final String RESOURCE_IDENTIFIER_BRAVE_SHIELDS="braveShields", RESOURCE_IDENTIFIER_TRACKERS="trackers", AGGRESSIVE="aggressive", DEFAULT="default";
 public static Profile lastProfile; public static String lastUrl, lastLevel; public static boolean enabled; public static int writes;
 public static boolean getShields(Profile p,String u,String r){return true;}
 public static String getShieldsValue(Profile p,String u,String r){return DEFAULT;}
 public static void setShields(Profile p,String u,String r,boolean v,boolean t){lastProfile=p;lastUrl=u;enabled=v;writes++;}
 public static void setShieldsValue(Profile p,String u,String r,String v,boolean t){lastProfile=p;lastUrl=u;lastLevel=v;writes++;}
 public static void resetSiteToDefaults(Profile p,String u){}
}