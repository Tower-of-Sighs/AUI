package com.sighs.apricityui.dev.debug;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugProtocolTest {
    @Test
    void systemInfoReportsProtocolVersion() {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", 1);
        request.addProperty("method", "System.info");
        JsonObject result = new DebugProtocolSession().handle(request).getAsJsonObject();

        assertEquals(1, result.get("protocolVersion").getAsInt());
        assertEquals(25321, ExternalDebugServer.PORT);
        assertTrue(result.get("endpoint").getAsString().contains(":25321/"));
    }

    @Test
    void tokenComparisonRejectsMissingAndDifferentTokens() {
        assertTrue(ExternalDebugServer.tokenMatches("secret", "secret"));
        assertFalse(ExternalDebugServer.tokenMatches("secret", "other"));
        assertFalse(ExternalDebugServer.tokenMatches("secret", null));
    }

    @Test
    void websocketHandshakeAcceptsTokenAndRejectsInvalidToken() throws Exception {
        assertEquals("HTTP/1.1 101 Switching Protocols", handshake("secret", "secret").get(0));
        assertEquals("HTTP/1.1 401 Unauthorized", handshake("secret", "wrong").get(0));
    }

    private static List<String> handshake(String expectedToken, String suppliedToken) throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            Thread connectionThread = new Thread(() -> {
                try {
                    Socket accepted = server.accept();
                    new DebugWebSocketConnection(accepted, expectedToken).run();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            connectionThread.start();

            List<String> response = new ArrayList<>();
            try (Socket client = new Socket(loopback, server.getLocalPort())) {
                client.setSoTimeout(3000);
                OutputStream output = client.getOutputStream();
                String request = "GET /apricity?token=" + suppliedToken + " HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n";
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) response.add(line);
            }
            connectionThread.join(3000);
            assertFalse(connectionThread.isAlive());
            return response;
        }
    }
}
