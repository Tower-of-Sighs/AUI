package com.sighs.apricityui.chunkmap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Hosts BlueMap's web renderer and versioned preview tiles on loopback. */
public final class BlueMapPreviewServer implements AutoCloseable {
    private static final Pattern TILE_PATH = Pattern.compile("x(-?\\d+)z(-?\\d+)\\.prbm");
    private static final byte[] PAGE_SETTINGS = ("{\"version\":\"5.24\",\"useCookies\":false,"
            + "\"maps\":[\"preview\"],\"mapDataRoot\":\"maps\",\"liveDataRoot\":\"maps\","
            + "\"styles\":[\"aui-preview.css\"],\"lowresSliderDefault\":0,"
            + "\"hiresSliderDefault\":320,\"hiresSliderMax\":640}")
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PREVIEW_STYLE = ("html,body,#map-container{width:100%;height:100%;}"
            + "#app{display:none!important;}*{user-select:none!important;}")
            .getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, byte[]> webAssets;
    private volatile BlueMapChunkTiles.Rendered current;
    private long revision;
    private long loadingRevision;
    private final ConcurrentSkipListMap<Long, BlueMapChunkTiles.Rendered> frames = new ConcurrentSkipListMap<>();

    public BlueMapPreviewServer() throws IOException {
        webAssets = loadWebAssets();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(requests);
        server.start();
    }

    public synchronized String publish(BlueMapChunkTiles.Rendered rendered) {
        current = rendered;
        revision++;
        frames.put(revision, rendered);
        frames.keySet().removeIf(key -> key < revision - 2 && key != loadingRevision);
        long span = span(rendered);
        float unit = presentationUnit(rendered);
        return "http://127.0.0.1:" + server.getAddress().getPort()
                + "/index.html#preview:"
                + rendered.centerX() / unit + ":" + rendered.centerY() / unit + ":" + rendered.centerZ() / unit + ":"
                + Math.max(80, Math.round(Math.max(span * 1.4F,
                        rendered.verticalRelief() * 2.2F) / unit))
                + ":0.75:0.7:0:0:perspective";
    }

    private static long span(BlueMapChunkTiles.Rendered rendered) {
        return rendered.span();
    }

    private static float presentationUnit(BlueMapChunkTiles.Rendered rendered) {
        return Math.max(1, rendered.span() / 256F);
    }

    private synchronized byte[] liveState() {
        if (current == null) return null;
        loadingRevision = revision;
        JsonObject state = new JsonObject();
        state.addProperty("revision", revision);
        state.addProperty("scale", current.scale());
        float unit = presentationUnit(current);
        state.addProperty("unit", unit);
        state.addProperty("renderScale", current.scale() / unit);
        state.addProperty("heightScale", current.verticalScale() / unit);
        state.addProperty("tileSize", 32F * current.scale() / unit);
        state.addProperty("translate", 2F * current.scale() / unit);
        state.addProperty("span", span(current));
        state.addProperty("x", current.centerX() / unit);
        state.addProperty("y", current.centerY() / unit);
        state.addProperty("z", current.centerZ() / unit);
        state.addProperty("distance", Math.max(80, Math.round(Math.max(span(current) * 1.4F,
                current.verticalRelief() * 2.2F) / unit)));
        state.addProperty("tileRoot", "/frames/" + revision + "/tiles/0/");
        JsonArray tiles = new JsonArray();
        current.tiles().keySet().forEach(tile -> {
            JsonArray key = new JsonArray(); key.add(tile.x()); key.add(tile.z()); tiles.add(key);
        });
        state.add("tiles", tiles);
        return state.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        byte[] body = null;
        String type = contentType(path);
        if (path.equals("/settings.json")) body = PAGE_SETTINGS;
        else if (path.equals("/preview-state.json")) body = liveState();
        else if (path.equals("/aui-live.js")) {
            try (InputStream stream = BlueMapPreviewServer.class.getResourceAsStream("/de/bluecolored/bluemap/aui-live.js")) {
                if (stream != null) body = stream.readAllBytes();
            }
        }
        else if (path.equals("/aui-preview.css")) body = PREVIEW_STYLE;
        else if (path.equals("/maps/preview/settings.json")) body = mapSettings();
        else if (path.equals("/maps/preview/textures.json")) {
            BlueMapChunkTiles.Rendered ready = current;
            if (ready != null) body = ready.texturesJson();
        } else if (path.startsWith("/frames/")) {
            String[] parts = path.split("/", 4);
            if (parts.length == 4 && parts[2].matches("\\d+") && parts[3].startsWith("tiles/0/")) {
                BlueMapChunkTiles.Rendered frame = frames.get(Long.parseLong(parts[2]));
                String coordinates = parts[3].substring("tiles/0/".length()).replace("/", "");
                Matcher tile = TILE_PATH.matcher(coordinates);
                if (frame != null && tile.matches()) body = frame.tiles().get(new BlueMapChunkTiles.Tile(
                        Integer.parseInt(tile.group(1)), Integer.parseInt(tile.group(2))));
            }
        } else if (path.startsWith("/maps/preview/tiles/0/")) {
            BlueMapChunkTiles.Rendered ready = current;
            Matcher tile = TILE_PATH.matcher(path.substring("/maps/preview/tiles/0/".length())
                    .replace("/", ""));
            if (ready != null && tile.matches()) {
                body = ready.tiles().get(new BlueMapChunkTiles.Tile(
                        Integer.parseInt(tile.group(1)), Integer.parseInt(tile.group(2))));
            }
        } else {
            body = webAssets.get(path.substring(1));
        }
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
        } else {
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        }
        exchange.close();
    }

    private byte[] mapSettings() {
        BlueMapChunkTiles.Rendered ready = current;
        if (ready == null) return null;
        float unit = presentationUnit(ready);
        float scale = ready.scale() / unit;
        String json = "{\"name\":\"Chunk preview\",\"startPos\":[" + ready.centerX() / unit
                + "," + ready.centerZ() / unit + "],\"skyColor\":[0.65,0.83,1],"
                + "\"voidColor\":[0.46,0.67,0.82],\"ambientLight\":0.2,"
                + "\"skyLight\":1,\"hires\":{\"tileSize\":[" + 32 * scale + "," + 32 * scale + "],"
                + "\"scale\":[" + scale + "," + scale + "],\"translate\":["
                + 2 * scale + "," + 2 * scale + "]},"
                + "\"lowres\":{\"lodCount\":0},\"perspectiveView\":true,"
                + "\"flatView\":true,\"freeFlightView\":true}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private static Map<String, byte[]> loadWebAssets() throws IOException {
        InputStream resource = BlueMapPreviewServer.class.getResourceAsStream(
                "/de/bluecolored/bluemap/webapp.zip");
        if (resource == null) throw new IOException("Bundled BlueMap webapp is missing");
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && !entry.getName().endsWith(".map")
                        && !entry.getName().endsWith(".php")) {
                    files.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        byte[] index = files.get("index.html");
        if (index != null) files.put("index.html", new String(index, StandardCharsets.UTF_8)
                .replace("</body>", "<script src=\"/aui-live.js\"></script></body>")
                .getBytes(StandardCharsets.UTF_8));
        return Map.copyOf(files);
    }

    @Override
    public void close() {
        server.stop(0);
        requests.shutdownNow();
    }
}
