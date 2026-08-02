package com.sighs.apricityui.dev.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.sighs.apricityui.init.Node;

final class DebugProtocolSession {
    private final Map<String, String> attachedTargets = new HashMap<>();

    JsonElement handle(JsonObject request) {
        if (request == null || !"2.0".equals(optionalString(request, "jsonrpc"))
                || !request.has("method") || !request.get("method").isJsonPrimitive()) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_REQUEST, "Invalid JSON-RPC request");
        }
        if (request.has("id") && !validRequestId(request.get("id"))) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_REQUEST,
                    "id must be a string, number, or null");
        }
        String method = request.get("method").getAsString();
        JsonObject params = parameters(request);
        return switch (method) {
            case "System.info" -> systemInfo();
            case "Target.list" -> listTargets();
            case "Target.attach" -> attach(params);
            case "Target.detach" -> detach(params);
            case "DOM.query" -> DebugDom.query(requireDocument(params), requiredString(params, "selector"));
            case "DOM.queryAll" -> DebugDom.queryAll(requireDocument(params), requiredString(params, "selector"));
            case "DOM.snapshot" -> snapshot(params);
            case "DOM.getAttributes" -> DebugDom.attributes(requireElement(params));
            case "DOM.getText" -> DebugDom.text(requireNode(params));
            case "DOM.getComputedStyle" -> DebugDom.computedStyle(requireElement(params));
            case "DOM.getBoxModel" -> boxModel(params);
            case "DOM.hover" -> input(params, InputAction.HOVER);
            case "DOM.click" -> input(params, InputAction.CLICK);
            case "DOM.fill" -> input(params, InputAction.FILL);
            default -> throw new DebugProtocolException(DebugProtocolException.METHOD_NOT_FOUND,
                    "Unknown method: " + method);
        };
    }

    void close() {
        attachedTargets.clear();
    }

    private JsonObject systemInfo() {
        JsonObject result = new JsonObject();
        result.addProperty("name", "Apricity Debug Protocol");
        result.addProperty("protocolVersion", ExternalDebugServer.PROTOCOL_VERSION);
        result.addProperty("endpoint", ExternalDebugServer.ENDPOINT);
        JsonArray capabilities = new JsonArray();
        capabilities.add("target");
        capabilities.add("dom");
        capabilities.add("input");
        result.add("capabilities", capabilities);
        return result;
    }

    private JsonObject listTargets() {
        JsonArray targets = new JsonArray();
        for (Document document : Document.getAll()) {
            if (document == null || document.isDisposed()) continue;
            JsonObject target = new JsonObject();
            target.addProperty("targetId", document.getUuid().toString());
            target.addProperty("path", document.getPath());
            target.addProperty("active", document.isActive());
            target.addProperty("inWorld", document.inWorld);
            target.addProperty("refreshGeneration", document.getRefreshGeneration());
            targets.add(target);
        }
        JsonObject result = new JsonObject();
        result.add("targets", targets);
        return result;
    }

    private JsonObject attach(JsonObject params) {
        String targetId = requiredString(params, "targetId");
        Document document = Document.getByUUID(targetId);
        if (document == null || !document.isActive()) {
            throw new DebugProtocolException(DebugProtocolException.TARGET_CLOSED, "Target is closed");
        }
        String sessionId = UUID.randomUUID().toString();
        attachedTargets.put(sessionId, targetId);
        JsonObject result = new JsonObject();
        result.addProperty("sessionId", sessionId);
        result.addProperty("targetId", targetId);
        result.addProperty("path", document.getPath());
        return result;
    }

    private JsonObject detach(JsonObject params) {
        String sessionId = requiredString(params, "sessionId");
        boolean detached = attachedTargets.remove(sessionId) != null;
        JsonObject result = new JsonObject();
        result.addProperty("detached", detached);
        return result;
    }

    private JsonObject snapshot(JsonObject params) {
        Document document = requireDocument(params);
        int maxDepth = boundedInt(params, "maxDepth", DebugDom.DEFAULT_MAX_DEPTH, 0, DebugDom.MAX_DEPTH);
        int maxNodes = boundedInt(params, "maxNodes", DebugDom.DEFAULT_MAX_NODES, 1, DebugDom.MAX_NODES);
        return DebugDom.snapshot(document, maxDepth, maxNodes);
    }

    private JsonObject boxModel(JsonObject params) {
        Document document = requireDocument(params);
        return DebugDom.boxModel(document, requireElement(document, params));
    }

    private JsonObject input(JsonObject params, InputAction action) {
        Document document = requireDocument(params);
        Element element = requireElement(document, params);
        return switch (action) {
            case HOVER -> DebugInput.hover(document, element);
            case CLICK -> DebugInput.click(document, element);
            case FILL -> DebugInput.fill(element, requiredString(params, "value", true));
        };
    }

    private Element requireElement(JsonObject params) {
        Document document = requireDocument(params);
        return requireElement(document, params);
    }

    private com.sighs.apricityui.init.Node requireNode(JsonObject params) {
        Document document = requireDocument(params);
        return DebugDom.requireNode(document, requiredString(params, "nodeId"));
    }

    private Element requireElement(Document document, JsonObject params) {
        return DebugDom.requireElement(document, requiredString(params, "nodeId"));
    }

    private Document requireDocument(JsonObject params) {
        String sessionId = requiredString(params, "sessionId");
        String targetId = attachedTargets.get(sessionId);
        if (targetId == null) {
            throw new DebugProtocolException(DebugProtocolException.TARGET_CLOSED, "Session is detached");
        }
        Document document = Document.getByUUID(targetId);
        if (document == null || !document.isActive()) {
            attachedTargets.remove(sessionId);
            throw new DebugProtocolException(DebugProtocolException.TARGET_CLOSED, "Target is closed");
        }
        return document;
    }

    private static JsonObject parameters(JsonObject request) {
        if (!request.has("params")) return new JsonObject();
        JsonElement params = request.get("params");
        if (!params.isJsonObject()) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_PARAMS, "params must be an object");
        }
        return params.getAsJsonObject();
    }

    private static String requiredString(JsonObject params, String name) {
        return requiredString(params, name, false);
    }

    private static String requiredString(JsonObject params, String name, boolean allowEmpty) {
        if (!params.has(name) || !params.get(name).isJsonPrimitive()
                || !params.get(name).getAsJsonPrimitive().isString()) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_PARAMS, name + " must be a string");
        }
        String value = params.get(name).getAsString();
        if (!allowEmpty && value.isBlank()) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_PARAMS, name + " must not be empty");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) return null;
        return object.get(name).getAsString();
    }

    private static int boundedInt(JsonObject params, String name, int fallback, int min, int max) {
        if (!params.has(name)) return fallback;
        try {
            if (!params.get(name).isJsonPrimitive() || !params.get(name).getAsJsonPrimitive().isNumber()) {
                throw new NumberFormatException();
            }
            int value = params.get(name).getAsInt();
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_PARAMS,
                    name + " must be between " + min + " and " + max);
        }
    }

    private static boolean validRequestId(JsonElement id) {
        if (id == null || id.isJsonNull()) return true;
        if (!id.isJsonPrimitive()) return false;
        return id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber();
    }

    private enum InputAction {
        HOVER,
        CLICK,
        FILL
    }
}
