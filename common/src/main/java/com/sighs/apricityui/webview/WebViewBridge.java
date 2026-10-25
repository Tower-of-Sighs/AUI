package com.sighs.apricityui.webview;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sighs.apricityui.spi.AuiWebViewService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Per-iframe, opt-in JSON RPC. Configure and tick on the Minecraft client thread.
 * Handlers run on that same thread and must not block. Async completions only enqueue
 * immutable JSON snapshots; no Minecraft state is accessed from completion threads.
 * Only explicitly allowed absolute page URLs (not entire origins) receive capabilities.
 */
public final class WebViewBridge implements AutoCloseable {
    private static final int MAX_BYTES = 65536;
    private static final int MAX_PENDING = 64;
    private final Set<URI> pages = new HashSet<>();
    private final Map<String, Handler> handlers = new HashMap<>();
    private final Map<String, Long> pending = new HashMap<>();
    private final ArrayDeque<Outgoing> outgoing = new ArrayDeque<>();
    private AuiWebViewService.View view;
    private volatile long generation = -1;
    private volatile String session;
    private URI source;
    private volatile boolean closed;

    private static JsonObject response(String targetSession, String id, JsonElement value, String code, String text) {
        JsonObject packet = new JsonObject();
        packet.addProperty("v", 1);
        packet.addProperty("type", "response");
        packet.addProperty("session", targetSession);
        packet.addProperty("id", id);
        packet.addProperty("ok", code == null);
        if (code == null) packet.add("result", value == null ? JsonNull.INSTANCE : value);
        else {
            JsonObject error = new JsonObject();
            error.addProperty("code", code);
            error.addProperty("message", text);
            packet.add("error", error);
        }
        return packet;
    }

    private static URI pageUri(String value) {
        URI uri = URI.create(value).normalize();
        String scheme = uri.getScheme();
        if (scheme == null || uri.isOpaque() || uri.getRawUserInfo() != null)
            throw new IllegalArgumentException("Absolute page URL required");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        // Preserve escapes: /a%2Fb and /a/b, or ?x=a%26b and ?x=a&b, can name different pages.
        if ("file".equalsIgnoreCase(scheme)) {
            if (uri.getAuthority() != null && !uri.getAuthority().isEmpty())
                throw new IllegalArgumentException("Remote file URLs are not trusted");
            return URI.create("file:" + path + query).normalize();
        }
        if ((!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) || uri.getHost() == null)
            throw new IllegalArgumentException("Only file/http/https page URLs can be trusted");
        int port = uri.getPort();
        if (("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80))
            port = -1;
        return URI.create(scheme.toLowerCase(java.util.Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(java.util.Locale.ROOT) + (port == -1 ? "" : ":" + port)
                + path + query).normalize();
    }

    /**
     * Deliberately exact-page scoped. Fragments do not change document authority.
     */
    public WebViewBridge allowPage(String url) {
        checkOpen();
        pages.add(pageUri(url));
        return this;
    }

    public WebViewBridge register(String method, Handler handler) {
        checkOpen();
        if (method == null || method.isBlank() || method.length() > 128)
            throw new IllegalArgumentException("Invalid bridge method");
        handlers.put(method, Objects.requireNonNull(handler));
        return this;
    }

    public void unregister(String method) {
        handlers.remove(method);
    }

    public boolean isReady() {
        return !closed && session != null;
    }

    /**
     * Called once per client tick, never from the render phase.
     */
    public void tick(AuiWebViewService.View current) {
        if (closed) return;
        if (view != current) {
            detach();
            view = current;
            if (view != null) view.enableBridge(true);
        }
        if (view == null) return;
        String[] messages = view.drainMessages();
        for (int i = 0; i + 2 < messages.length; i += 3) {
            try {
                receive(Long.parseLong(messages[i]), messages[i + 1], messages[i + 2]);
            } catch (RuntimeException ignored) { /* Malformed/untrusted packets grant nothing. */ }
        }
        // Expire async calls; a late completion is ignored, never routed to a new session.
        long now = System.nanoTime();
        pending.entrySet().removeIf(entry -> now - entry.getValue() > 60_000_000_000L);
        for (int i = 0; i < 32; i++) {
            Outgoing next;
            synchronized (outgoing) {
                next = outgoing.peekFirst();
            }
            if (next == null) break;
            JsonObject packet = JsonParser.parseString(next.json).getAsJsonObject();
            if (next.generation == generation && Objects.equals(session, packet.get("session").getAsString())) {
                if (!view.postMessage(next.generation, next.json)) break;
                if ("response".equals(packet.get("type").getAsString())) pending.remove(packet.get("id").getAsString());
            }
            synchronized (outgoing) {
                outgoing.removeFirst();
            }
        }
    }

    private void receive(long epoch, String actualSource, String json) {
        // Empty source is an internal native navigation notification, never a page message.
        if (actualSource.isEmpty() && json.isEmpty()) {
            reset();
            generation = epoch;
            return;
        }
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return;
        URI uri = pageUri(actualSource);
        if (!pages.contains(uri)) return;
        JsonObject message = JsonParser.parseString(json).getAsJsonObject();
        if (message.get("v").getAsInt() != 1) return;
        String incomingSession = message.get("session").getAsString();
        if (incomingSession.isEmpty() || incomingSession.length() > 128) return;
        String type = message.get("type").getAsString();
        if ("hello".equals(type)) {
            if (epoch < generation) return;
            if (epoch != generation || !incomingSession.equals(session) || !uri.equals(source)) {
                reset();
                generation = epoch;
                session = incomingSession;
                source = uri;
            }
            enqueue(epoch, packet("ready"));
            return;
        }
        if (epoch != generation || !incomingSession.equals(session) || !uri.equals(source)
                || !"request".equals(type)) return;
        String id = message.get("id").getAsString();
        if (id.isEmpty() || id.length() > 128) return;
        if (pending.containsKey(id)) return;
        String method = message.get("method").getAsString();
        if (pending.size() >= MAX_PENDING) {
            respond(epoch, incomingSession, id, null, "QUEUE_FULL", "Too many pending requests");
            return;
        }
        Handler handler = handlers.get(method);
        if (handler == null) {
            respond(epoch, incomingSession, id, null, "METHOD_NOT_FOUND", "Unknown method");
            return;
        }
        pending.put(id, System.nanoTime());
        try {
            JsonElement params = message.has("params") ? message.get("params") : JsonNull.INSTANCE;
            CompletionStage<JsonElement> result = Objects.requireNonNull(
                    handler.handle(new Context(uri, session), params));
            result.whenComplete((value, failure) -> {
                // Snapshot on the completing thread. Tick later verifies the session again.
                JsonObject response = response(incomingSession, id, value,
                        failure == null ? null : "JAVA_ERROR", failure == null ? null : "Java handler failed");
                enqueue(epoch, response);
            });
        } catch (Exception failure) {
            respond(epoch, incomingSession, id, null, "JAVA_ERROR", "Java handler failed");
        }
    }

    /**
     * Returns false before handshake or when the bounded outgoing queue is full.
     */
    public boolean emit(String event, JsonElement data) {
        if (!isReady() || event == null || event.isBlank() || event.length() > 128) return false;
        JsonObject packet = packet("event");
        packet.addProperty("event", event);
        packet.add("data", data == null ? JsonNull.INSTANCE : data);
        return enqueue(generation, packet);
    }

    private void respond(long epoch, String targetSession, String id, JsonElement value, String code, String text) {
        enqueue(epoch, response(targetSession, id, value, code, text));
    }

    private JsonObject packet(String type) {
        JsonObject packet = new JsonObject();
        packet.addProperty("v", 1);
        packet.addProperty("type", type);
        packet.addProperty("session", session);
        return packet;
    }

    private boolean enqueue(long epoch, JsonObject packet) {
        String json = packet.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            if (!"response".equals(packet.get("type").getAsString())) return false;
            json = response(packet.get("session").getAsString(), packet.get("id").getAsString(),
                    null, "MESSAGE_TOO_LARGE", "Response exceeds 64 KiB").toString();
        }
        synchronized (outgoing) {
            if (closed || epoch != generation || !Objects.equals(session, packet.get("session").getAsString())
                    || outgoing.size() >= 256) return false;
            outgoing.addLast(new Outgoing(epoch, json));
        }
        return true;
    }

    /**
     * Drop transport state before destroying the native view; retain configured methods.
     */
    public void detach() {
        if (view != null) {
            if (session != null) view.postMessage(generation, packet("close").toString());
            view.enableBridge(false);
        }
        view = null;
        reset();
    }

    private void reset() {
        session = null;
        source = null;
        generation = -1;
        pending.clear();
        synchronized (outgoing) {
            outgoing.clear();
        }
    }

    @Override
    public void close() {
        detach();
        closed = true;
        handlers.clear();
        pages.clear();
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Bridge closed");
    }

    @FunctionalInterface
    public interface Handler {
        CompletionStage<JsonElement> handle(Context context, JsonElement params) throws Exception;
    }

    public record Context(URI source, String session) {
    }

    private record Outgoing(long generation, String json) {
    }
}
