package org.chromium.url;
public class GURL {
    private String spec;
    public GURL(){this("https://example.com/");}
    public GURL(String value){spec=value;}
    public String getSpec(){return spec;}
    public String getHost(){return "example.com";}
    public String getScheme(){return spec.split(":",2)[0];}
    public boolean isValid(){return spec.contains("://") && !spec.endsWith("://");}
}
