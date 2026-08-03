package com.sighs.apricityui.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class BrowserLocation {
    private final String href;
    private final String protocol;
    private final String host;
    private final String hostname;
    private final String port;
    private final String origin;
    private final String pathname;
    private final String search;
    private final String hash;

    public BrowserLocation(String href) {
        String rawHref = href == null ? "" : href;
        String resolvedProtocol = "";
        String resolvedHost = "";
        String resolvedHostname = "";
        String resolvedPort = "";
        String resolvedOrigin = "";
        String resolvedPathname = rawHref;
        String resolvedSearch = "";
        String resolvedHash = "";

        try {
            URI uri = new URI(rawHref);
            if (uri.getScheme() != null && !uri.getScheme().isBlank()) {
                resolvedProtocol = uri.getScheme() + ":";
            }
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                resolvedHostname = uri.getHost();
                resolvedPort = uri.getPort() >= 0 ? String.valueOf(uri.getPort()) : "";
                resolvedHost = resolvedPort.isEmpty() ? resolvedHostname : resolvedHostname + ":" + resolvedPort;
                resolvedOrigin = resolvedProtocol.isEmpty() ? "" : resolvedProtocol + "//" + resolvedHost;
            }
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
                resolvedPathname = uri.getRawPath();
            } else if (resolvedHostname.isEmpty()) {
                resolvedPathname = stripQueryAndHash(rawHref);
            } else {
                resolvedPathname = "";
            }
            if (uri.getRawQuery() != null) {
                resolvedSearch = "?" + uri.getRawQuery();
            }
            if (uri.getRawFragment() != null) {
                resolvedHash = "#" + uri.getRawFragment();
            }
        } catch (URISyntaxException ignored) {
            resolvedPathname = stripQueryAndHash(rawHref);
            int queryIndex = rawHref.indexOf('?');
            int hashIndex = rawHref.indexOf('#');
            if (queryIndex >= 0) {
                int queryEnd = hashIndex >= 0 && hashIndex > queryIndex ? hashIndex : rawHref.length();
                resolvedSearch = rawHref.substring(queryIndex, queryEnd);
            }
            if (hashIndex >= 0) {
                resolvedHash = rawHref.substring(hashIndex);
            }
        }

        this.href = rawHref;
        this.protocol = resolvedProtocol;
        this.host = resolvedHost;
        this.hostname = resolvedHostname;
        this.port = resolvedPort;
        this.origin = resolvedOrigin;
        this.pathname = resolvedPathname == null ? "" : resolvedPathname;
        this.search = resolvedSearch;
        this.hash = resolvedHash;
    }

    public String getHref() {
        return href;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public String getHostname() {
        return hostname;
    }

    public String getPort() {
        return port;
    }

    public String getOrigin() {
        return origin;
    }

    public String getPathname() {
        return pathname;
    }

    public String getSearch() {
        return search;
    }

    public String getHash() {
        return hash;
    }

    public void assign(String ignoredHref) {
    }

    public void replace(String ignoredHref) {
    }

    public void reload() {
    }

    private static String stripQueryAndHash(String href) {
        if (href == null || href.isEmpty()) return "";
        int queryIndex = href.indexOf('?');
        int hashIndex = href.indexOf('#');
        int end = href.length();
        if (queryIndex >= 0) end = Math.min(end, queryIndex);
        if (hashIndex >= 0) end = Math.min(end, hashIndex);
        return href.substring(0, end);
    }
}
