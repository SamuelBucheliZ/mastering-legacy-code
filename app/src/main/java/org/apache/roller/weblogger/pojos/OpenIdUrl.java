package org.apache.roller.weblogger.pojos;

public class OpenIdUrl {
    private final String openIdUrl;

    public OpenIdUrl(String openIdUrl) {
        if (openIdUrl != null && openIdUrl.endsWith("/")) {
            this.openIdUrl = openIdUrl.substring(0, openIdUrl.length() - 1);
        } else {
            this.openIdUrl = openIdUrl;
        }
    }

    public String getOpenIdUrl() {
        return openIdUrl;
    }

    public String getStateString() {
        return "OpenIdUrl{" +
                "openIdUrl='" + openIdUrl + '\'' +
                '}';
    }
}
