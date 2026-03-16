package com.claudeplugin.jmeter.har;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Parses HAR (HTTP Archive) files and extracts HTTP request details.
 * HAR files can be exported from: Chrome DevTools, Firefox, Burp Suite, Fiddler, Charles Proxy.
 *
 * HOW TO EXPORT HAR from Chrome:
 *   1. Open DevTools (F12) → Network tab
 *   2. Record your session
 *   3. Right-click any request → "Save all as HAR with content"
 */
public class HarParser {

    public static class HarRequest {
        public String method;
        public String url;
        public String host;
        public String path;
        public Map<String, String> headers = new LinkedHashMap<>();
        public Map<String, String> queryParams = new LinkedHashMap<>();
        public String postBody;
        public String mimeType;
        public int responseStatus;
        public String responseBody; // first 2000 chars for correlation detection

        @Override
        public String toString() {
            return method + " " + url;
        }
    }

    /**
     * Parse a HAR file and return all HTTP requests found inside.
     */
    public static List<HarRequest> parse(File harFile) throws IOException {
        String content = new String(Files.readAllBytes(harFile.toPath()));
        return parseJson(content);
    }

    public static List<HarRequest> parseJson(String harJson) {
        List<HarRequest> requests = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(harJson).getAsJsonObject();
            JsonObject log = root.getAsJsonObject("log");
            JsonArray entries = log.getAsJsonArray("entries");

            for (JsonElement entry : entries) {
                JsonObject e = entry.getAsJsonObject();
                JsonObject req = e.getAsJsonObject("request");

                HarRequest r = new HarRequest();
                r.method = req.get("method").getAsString();
                r.url    = req.get("url").getAsString();

                // Parse host and path from URL
                try {
                    java.net.URL u = new java.net.URL(r.url);
                    r.host = u.getProtocol() + "://" + u.getHost() +
                             (u.getPort() != -1 ? ":" + u.getPort() : "");
                    r.path = u.getPath();
                    if (u.getQuery() != null) {
                        r.path += "?" + u.getQuery();
                        // Parse individual query params
                        for (String param : u.getQuery().split("&")) {
                            String[] kv = param.split("=", 2);
                            if (kv.length == 2) r.queryParams.put(kv[0], kv[1]);
                        }
                    }
                } catch (Exception ignored) { r.host = ""; r.path = r.url; }

                // Headers
                for (JsonElement h : req.getAsJsonArray("headers")) {
                    JsonObject hObj = h.getAsJsonObject();
                    String name = hObj.get("name").getAsString();
                    // Skip internal/browser headers not needed in JMeter
                    if (!name.startsWith(":") && !name.equalsIgnoreCase("content-length")) {
                        r.headers.put(name, hObj.get("value").getAsString());
                    }
                }

                // Query params (from queryString array if present)
                if (req.has("queryString")) {
                    for (JsonElement qs : req.getAsJsonArray("queryString")) {
                        JsonObject qObj = qs.getAsJsonObject();
                        r.queryParams.put(qObj.get("name").getAsString(),
                                          qObj.get("value").getAsString());
                    }
                }

                // POST body
                if (req.has("postData")) {
                    JsonObject pd = req.getAsJsonObject("postData");
                    r.mimeType = pd.has("mimeType") ? pd.get("mimeType").getAsString() : "";
                    r.postBody = pd.has("text") ? pd.get("text").getAsString() : "";
                }

                // Response status + body snippet (for correlation detection)
                if (e.has("response")) {
                    JsonObject resp = e.getAsJsonObject("response");
                    r.responseStatus = resp.get("status").getAsInt();
                    if (resp.has("content")) {
                        JsonObject content = resp.getAsJsonObject("content");
                        if (content.has("text")) {
                            String body = content.get("text").getAsString();
                            r.responseBody = body.length() > 2000 ? body.substring(0, 2000) : body;
                        }
                    }
                }

                // Filter out static assets (images, fonts, css, js from CDNs)
                if (!isStaticAsset(r.url)) {
                    requests.add(r);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse HAR: " + ex.getMessage(), ex);
        }
        return requests;
    }

    /**
     * Convert parsed requests to a compact summary string for sending to Claude.
     * Truncates large bodies to keep token count reasonable.
     */
    public static String toClaudeSummary(List<HarRequest> requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total HTTP requests found: ").append(requests.size()).append("\n\n");
        int i = 1;
        for (HarRequest r : requests) {
            sb.append("--- Request ").append(i++).append(" ---\n");
            sb.append("Method: ").append(r.method).append("\n");
            sb.append("URL: ").append(r.url).append("\n");
            if (r.postBody != null && !r.postBody.isEmpty()) {
                String body = r.postBody.length() > 500 ? r.postBody.substring(0, 500) + "...[truncated]" : r.postBody;
                sb.append("Body: ").append(body).append("\n");
            }
            // Include auth headers as hints for correlation
            r.headers.forEach((k, v) -> {
                if (k.equalsIgnoreCase("Authorization") ||
                    k.equalsIgnoreCase("X-CSRF-Token") ||
                    k.equalsIgnoreCase("Cookie")) {
                    sb.append("Header ").append(k).append(": ").append(v, 0, Math.min(80, v.length())).append("...\n");
                }
            });
            if (r.responseBody != null) {
                String rb = r.responseBody.length() > 300 ? r.responseBody.substring(0, 300) + "..." : r.responseBody;
                sb.append("Response snippet: ").append(rb).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isStaticAsset(String url) {
        String lower = url.toLowerCase();
        return lower.contains(".png") || lower.contains(".jpg") || lower.contains(".gif") ||
               lower.contains(".css") || lower.contains(".woff") || lower.contains(".ico") ||
               lower.contains("google-analytics") || lower.contains("doubleclick") ||
               lower.contains("googletagmanager") || lower.contains("facebook.net");
    }
}
