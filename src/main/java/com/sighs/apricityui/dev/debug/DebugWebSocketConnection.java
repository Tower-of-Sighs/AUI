package com.sighs.apricityui.dev.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class DebugWebSocketConnection implements Runnable {
    private static final String PATH = "/apricity";
    private static final String WEB_SOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HTTP_HEADER = 64 * 1024;
    private static final int MAX_FRAME_PAYLOAD = 1024 * 1024;
    private final Socket socket;
    private final String token;
    private final DebugProtocolSession session = new DebugProtocolSession();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Object outputLock = new Object();
    private volatile OutputStream output;

    DebugWebSocketConnection(Socket socket, String token) {
        this.socket = socket;
        this.token = token;
    }

    @Override
    public void run() {
        try (socket; BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            socket.setSoTimeout(10_000);
            output = socket.getOutputStream();
            if (!handshake(input)) return;
            socket.setSoTimeout(0);
            readFrames(input);
        } catch (IOException ignored) {
        } finally {
            open.set(false);
            ExternalDebugServer.connectionClosed(this, session);
        }
    }

    boolean isOpen() {
        return open.get() && !socket.isClosed();
    }

    void sendText(String text) {
        if (!isOpen() || output == null) return;
        sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    void close() {
        if (!open.compareAndSet(true, false)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private boolean handshake(InputStream input) throws IOException {
        String headerText = readHttpHeader(input);
        if (headerText == null) return false;
        String[] lines = headerText.split("\\r\\n");
        if (lines.length == 0) return reject(400, "Bad Request");
        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length < 2 || !"GET".equals(requestLine[0])) return reject(400, "Bad Request");

        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator <= 0) continue;
            headers.put(lines[index].substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    lines[index].substring(separator + 1).trim());
        }

        URI uri;
        try {
            uri = URI.create(requestLine[1]);
        } catch (IllegalArgumentException invalidUri) {
            return reject(400, "Bad Request");
        }
        String suppliedToken = queryParameter(uri.getRawQuery(), "token");
        if (suppliedToken == null) suppliedToken = bearerToken(headers.get("authorization"));
        if (!PATH.equals(uri.getPath()) || !ExternalDebugServer.tokenMatches(token, suppliedToken)) {
            return reject(401, "Unauthorized");
        }

        String key = headers.get("sec-websocket-key");
        if (key == null || !"13".equals(headers.get("sec-websocket-version"))
                || !"websocket".equalsIgnoreCase(headers.get("upgrade"))
                || !hasHeaderToken(headers.get("connection"), "upgrade")) {
            return reject(400, "Bad WebSocket Handshake");
        }
        String accept = webSocketAccept(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        return true;
    }

    private void readFrames(InputStream input) throws IOException {
        ByteArrayOutputStream fragmented = null;
        int fragmentedOpcode = 0;
        while (open.get()) {
            Frame frame = readFrame(input);
            if (frame == null) return;
            switch (frame.opcode) {
                case 0x0 -> {
                    if (fragmented == null) throw new IOException("Unexpected continuation frame");
                    fragmented.write(frame.payload);
                    if (fragmented.size() > MAX_FRAME_PAYLOAD) throw new IOException("Message is too large");
                    if (frame.fin) {
                        dispatchMessage(fragmentedOpcode, fragmented.toByteArray());
                        fragmented = null;
                        fragmentedOpcode = 0;
                    }
                }
                case 0x1 -> {
                    if (fragmented != null) throw new IOException("Interleaved fragmented message");
                    if (frame.fin) dispatchMessage(frame.opcode, frame.payload);
                    else {
                        fragmented = new ByteArrayOutputStream();
                        fragmented.write(frame.payload);
                        fragmentedOpcode = frame.opcode;
                    }
                }
                case 0x8 -> {
                    sendFrame(0x8, frame.payload.length <= 125 ? frame.payload : new byte[0]);
                    return;
                }
                case 0x9 -> sendFrame(0xA, frame.payload);
                case 0xA -> {
                }
                default -> throw new IOException("Unsupported WebSocket opcode");
            }
        }
    }

    private void dispatchMessage(int opcode, byte[] payload) {
        if (opcode != 0x1) return;
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                ExternalDebugServer.writeError(this, JsonNull.INSTANCE,
                        DebugProtocolException.INVALID_REQUEST, "JSON-RPC request must be an object");
                return;
            }
            ExternalDebugServer.enqueue(session, this, parsed.getAsJsonObject());
        } catch (JsonParseException parseError) {
            ExternalDebugServer.writeError(this, JsonNull.INSTANCE, -32700, "Parse error");
        }
    }

    private Frame readFrame(InputStream input) throws IOException {
        int first = input.read();
        if (first < 0) return null;
        int second = readByte(input);
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        if (!masked) throw new IOException("Client WebSocket frames must be masked");
        long length = second & 0x7F;
        if (length == 126) length = readUnsignedShort(input);
        else if (length == 127) length = readLong(input);
        if (length < 0 || length > MAX_FRAME_PAYLOAD) throw new IOException("Frame is too large");
        if (opcode >= 0x8 && (!fin || length > 125)) throw new IOException("Invalid control frame");
        byte[] mask = readExactly(input, 4);
        byte[] payload = readExactly(input, (int) length);
        for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index & 3];
        return new Frame(fin, opcode, payload);
    }

    private void sendFrame(int opcode, byte[] payload) {
        OutputStream target = output;
        if (target == null || !isOpen()) return;
        synchronized (outputLock) {
            try {
                target.write(0x80 | (opcode & 0x0F));
                if (payload.length <= 125) {
                    target.write(payload.length);
                } else if (payload.length <= 0xFFFF) {
                    target.write(126);
                    target.write((payload.length >>> 8) & 0xFF);
                    target.write(payload.length & 0xFF);
                } else {
                    target.write(127);
                    target.write(ByteBuffer.allocate(8).putLong(payload.length).array());
                }
                target.write(payload);
                target.flush();
            } catch (IOException failure) {
                close();
            }
        }
    }

    private boolean reject(int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + status + " " + message + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
        return false;
    }

    private static String readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < MAX_HTTP_HEADER) {
            int value = input.read();
            if (value < 0) return null;
            bytes.write(value);
            matched = switch (matched) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : (value == '\r' ? 1 : 0);
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> 4;
            };
            if (matched == 4) return bytes.toString(StandardCharsets.US_ASCII);
        }
        throw new IOException("HTTP header is too large");
    }

    private static String queryParameter(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String rawName = separator < 0 ? part : part.substring(0, separator);
            if (!name.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) continue;
            String value = separator < 0 ? "" : part.substring(separator + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) return null;
        return authorization.substring(7).trim();
    }

    private static boolean hasHeaderToken(String header, String expected) {
        if (header == null) return false;
        for (String token : header.split(",")) {
            if (expected.equalsIgnoreCase(token.trim())) return true;
        }
        return false;
    }

    private static String webSocketAccept(String key) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((key.trim() + WEB_SOCKET_GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-1 is unavailable", impossible);
        }
    }

    private static int readByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException();
        return value;
    }

    private static int readUnsignedShort(InputStream input) throws IOException {
        return (readByte(input) << 8) | readByte(input);
    }

    private static long readLong(InputStream input) throws IOException {
        long value = 0;
        for (int index = 0; index < 8; index++) value = (value << 8) | readByte(input);
        return value;
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) throw new EOFException();
            offset += read;
        }
        return result;
    }

    private record Frame(boolean fin, int opcode, byte[] payload) {
    }
}
