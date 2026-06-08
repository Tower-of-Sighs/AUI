package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.canvas.CanvasPath2D;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.instance.dom.DocumentExpander;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Document {
    private enum LifecycleState {
        LOADING("loading"),
        INTERACTIVE("interactive"),
        COMPLETE("complete"),
        DISPOSED("complete");

        private final String readyStateValue;

        LifecycleState(String readyStateValue) {
            this.readyStateValue = readyStateValue;
        }
    }

    private static final List<Document> documents = new CopyOnWriteArrayList<>();
    private final ElementTree tree = new ElementTree(this);
    private final RenderQueue render = new RenderQueue(this);
    private final String path;
    public final Map<String, Map<String, String>> CSSCache = new LinkedHashMap<>();
    public final List<CSS.DebugRule> CSSDebugRules = new ArrayList<>();
    public final List<String> JSCache = new ArrayList<>();
    public Body body;
    private final UUID uuid = UUID.randomUUID();
    public final boolean inWorld;
    private volatile boolean reloadPersistent = false;
    private volatile long refreshGeneration = 0L;
    private volatile LifecycleState lifecycleState = LifecycleState.LOADING;
    private volatile String readyState = LifecycleState.LOADING.readyStateValue;
    private volatile Element lastClickTarget = null;
    private volatile int lastClickButton = -1;
    private volatile long lastClickTimeNs = 0L;
    private final CopyOnWriteArrayList<MutationObserver> mutationObservers = new CopyOnWriteArrayList<>();

    private final StyleScope style = new StyleScope(this);
    private final MotionTrack motion = new MotionTrack(this);
    private final FocusRing focus = new FocusRing(this);

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void refresh() {
        beginRefreshLifecycle();
        CSSCache.clear();
        CSSDebugRules.clear();
        JSCache.clear();
        tree.clear();
        render.reset();
        motion.clear();
        invalidateSelectorIndex();
        Element bodyElement = HTML.create(this, path);
        try {
            if (bodyElement == null) return;
            if (body != null) bodyElement.setEventListeners(body.EventListener);
            body = (Body) Element.init(bodyElement);
            rebuildElementIndexFromBody();

            // First pass: ensure computed styles exist for DOM expanders.
            style.recomputeSubtree(body);
            DocumentExpander.apply(this);

            // Final pass: apply styles once after expansion.
            style.recomputeSubtree(body);
            tree.getElements().forEach(Element::clearDirtyFlags);
            render.reset();
            render.rebuildPaintList();
            ImageAsyncHandler.prefetchImages(this);
            enterInteractive();

            for (String js : JSCache) {
                String head = "let document = ApricityUI.getDocumentByUUID(\"" + uuid + "\");\n";
                head += "let window = ApricityUI.getWindow();\n";
                head += "let console = window.getConsole();\n";
                head += "let localStorage = window.getLocalStorage();\n";
                head += "let sessionStorage = window.getSessionStorage();\n";
                head += "let performance = window.getPerformance();\n";
                head += "let getComputedStyle = (element) => window.getComputedStyle(element);\n";
                head += "let fetch = (url) => {\n";
                head += "  let p = window.fetch(url, document.getBaseURI());\n";
                head += "  p['catch'] = (fn) => p.catchError(fn);\n";
                head += "  return p;\n";
                head += "};\n";
                head += "let requestAnimationFrame = (callback) => window.requestAnimationFrame(callback);\n";
                head += "let cancelAnimationFrame = (id) => window.cancelAnimationFrame(id);\n";
                head += "let setTimeout = (callback, delay) => window.setTimeout(callback, delay == null ? 0 : delay);\n";
                head += "let clearTimeout = (handle) => window.clearTimeout(handle);\n";
                head += "let setInterval = (callback, delay) => window.setInterval(callback, delay == null ? 0 : delay);\n";
                head += "let clearInterval = (handle) => window.clearInterval(handle);\n";
                head += "let createImageBitmap = function(source, sx, sy, sw, sh) {\n";
                head += "  if (arguments.length >= 5) return window.createImageBitmap(source, Number(sx), Number(sy), Number(sw), Number(sh));\n";
                head += "  return window.createImageBitmap(source);\n";
                head += "};\n";
                head += "let createImageBitmapAsync = function(source, sx, sy, sw, sh) {\n";
                head += "  let p = arguments.length >= 5\n";
                head += "    ? window.createImageBitmapAsync(source, Number(sx), Number(sy), Number(sw), Number(sh))\n";
                head += "    : window.createImageBitmapAsync(source);\n";
                head += "  p['catch'] = (fn) => p.catchError(fn);\n";
                head += "  return p;\n";
                head += "};\n";
                head += "function OffscreenCanvas(width, height) { return window.createOffscreenCanvas(Number(width) || 0, Number(height) || 0); }\n";
                head += "function DOMMatrix(init) { return arguments.length === 0 ? window.createDOMMatrix() : window.createDOMMatrix(init); }\n";
                head += "try {\n";
                head += "  Object.defineProperty(document, 'readyState', { get: () => document.getReadyState() });\n";
                head += "  Object.defineProperty(document, 'activeElement', { get: () => document.getActiveElement() });\n";
                head += "  Object.defineProperty(window, 'innerWidth', { get: () => window.getInnerWidth() });\n";
                head += "  Object.defineProperty(window, 'innerHeight', { get: () => window.getInnerHeight() });\n";
                head += "  Object.defineProperty(window, 'devicePixelRatio', { get: () => window.getDevicePixelRatio() });\n";
                head += "  Object.defineProperty(localStorage, 'length', { get: () => localStorage.getLength() });\n";
                head += "  Object.defineProperty(sessionStorage, 'length', { get: () => sessionStorage.getLength() });\n";
                head += "} catch (e) {}\n";
                head += "function Event(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  return window.createEvent(type, !!init.bubbles);\n";
                head += "}\n";
                head += "function CustomEvent(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  return window.createCustomEvent(type, init.detail, !!init.bubbles);\n";
                head += "}\n";
                head += "function MouseEvent(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  let x = init.clientX || 0;\n";
                head += "  let y = init.clientY || 0;\n";
                head += "  let button = init.button == null ? -1 : init.button;\n";
                head += "  return window.createMouseEvent(type, x, y, button);\n";
                head += "}\n";
                head += "function WheelEvent(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  let x = init.clientX || 0;\n";
                head += "  let y = init.clientY || 0;\n";
                head += "  let dx = Number(init.deltaX || 0);\n";
                head += "  let dy = Number(init.deltaY || 0);\n";
                head += "  let mode = init.deltaMode == null ? 0 : Number(init.deltaMode);\n";
                head += "  return window.createWheelEvent(type, x, y, dx, dy, mode);\n";
                head += "}\n";
                head += "function PointerEvent(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  let x = init.clientX || 0;\n";
                head += "  let y = init.clientY || 0;\n";
                head += "  let button = init.button == null ? -1 : Number(init.button);\n";
                head += "  let pointerId = init.pointerId == null ? 1 : Number(init.pointerId);\n";
                head += "  let pointerType = init.pointerType == null ? 'mouse' : String(init.pointerType);\n";
                head += "  let isPrimary = init.isPrimary == null ? true : !!init.isPrimary;\n";
                head += "  return window.createPointerEvent(type, x, y, button, pointerId, pointerType, isPrimary);\n";
                head += "}\n";
                head += "function __auiDecorateResponse(resp) {\n";
                head += "  if (!resp || resp.__auiDecoratedResponse) return resp;\n";
                head += "  try {\n";
                head += "    Object.defineProperty(resp, '__auiDecoratedResponse', { value: true });\n";
                head += "    Object.defineProperty(resp, 'ok', { get: () => resp.isOk() });\n";
                head += "    Object.defineProperty(resp, 'status', { get: () => resp.getStatus() });\n";
                head += "    Object.defineProperty(resp, 'url', { get: () => resp.getUrl() });\n";
                head += "  } catch (e) {}\n";
                head += "  return resp;\n";
                head += "}\n";
                head += "function __auiDecorateList(list) {\n";
                head += "  if (!list) return [];\n";
                head += "  let out = [];\n";
                head += "  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);\n";
                head += "  for (let i = 0; i < size; i++) {\n";
                head += "    let item = typeof list.get === 'function' ? list.get(i) : list[i];\n";
                head += "    out.push(__auiDecorateElement(item));\n";
                head += "  }\n";
                head += "  return out;\n";
                head += "}\n";
                head += "function __auiDecorateResizeEntries(list) {\n";
                head += "  if (!list) return [];\n";
                head += "  let out = [];\n";
                head += "  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);\n";
                head += "  for (let i = 0; i < size; i++) {\n";
                head += "    let entry = typeof list.get === 'function' ? list.get(i) : list[i];\n";
                head += "    if (!entry) continue;\n";
                head += "    let rect = entry.contentRect;\n";
                head += "    out.push({\n";
                head += "      target: __auiDecorateElement(entry.target),\n";
                head += "      contentRect: rect,\n";
                head += "      borderBoxSize: [{ inlineSize: rect.borderBoxWidth, blockSize: rect.borderBoxHeight }],\n";
                head += "      contentBoxSize: [{ inlineSize: rect.width, blockSize: rect.height }]\n";
                head += "    });\n";
                head += "  }\n";
                head += "  return out;\n";
                head += "}\n";
                head += "function __auiDecorateMutationRecords(list) {\n";
                head += "  if (!list) return [];\n";
                head += "  let out = [];\n";
                head += "  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);\n";
                head += "  for (let i = 0; i < size; i++) {\n";
                head += "    let record = typeof list.get === 'function' ? list.get(i) : list[i];\n";
                head += "    if (!record) continue;\n";
                head += "    out.push({\n";
                head += "      type: record.type,\n";
                head += "      target: __auiDecorateElement(record.target),\n";
                head += "      addedNodes: __auiDecorateList(record.addedNodes),\n";
                head += "      removedNodes: __auiDecorateList(record.removedNodes),\n";
                head += "      previousSibling: __auiDecorateElement(record.previousSibling),\n";
                head += "      nextSibling: __auiDecorateElement(record.nextSibling),\n";
                head += "      attributeName: record.attributeName,\n";
                head += "      oldValue: record.oldValue\n";
                head += "    });\n";
                head += "  }\n";
                head += "  return out;\n";
                head += "}\n";
                head += "function __auiDecorateTokenList(list) {\n";
                head += "  if (!list || list.__auiDecoratedTokenList) return list;\n";
                head += "  try {\n";
                head += "    Object.defineProperty(list, '__auiDecoratedTokenList', { value: true });\n";
                head += "    Object.defineProperty(list, 'length', { get: () => list.getLength() });\n";
                head += "    if (typeof list.add === 'function') {\n";
                head += "      let add = list.add;\n";
                head += "      list.add = function() { return add.apply(list, arguments); };\n";
                head += "    }\n";
                head += "    if (typeof list.remove === 'function') {\n";
                head += "      let remove = list.remove;\n";
                head += "      list.remove = function() { return remove.apply(list, arguments); };\n";
                head += "    }\n";
                head += "  } catch (e) {}\n";
                head += "  return list;\n";
                head += "}\n";
                head += "function __auiSyncDatasetProperties(dataset) {\n";
                head += "  if (!dataset || typeof dataset.keys !== 'function') return dataset;\n";
                head += "  let keys = dataset.keys();\n";
                head += "  let size = typeof keys.size === 'function' ? keys.size() : (keys.length || 0);\n";
                head += "  for (let i = 0; i < size; i++) {\n";
                head += "    let key = typeof keys.get === 'function' ? keys.get(i) : keys[i];\n";
                head += "    if (!key || dataset.__auiDatasetKeys[key]) continue;\n";
                head += "    dataset.__auiDatasetKeys[key] = true;\n";
                head += "    try {\n";
                head += "      Object.defineProperty(dataset, key, {\n";
                head += "        get: () => dataset.get(key),\n";
                head += "        set: (value) => dataset.set(key, value == null ? '' : String(value)),\n";
                head += "        enumerable: true,\n";
                head += "        configurable: true\n";
                head += "      });\n";
                head += "    } catch (e) {}\n";
                head += "  }\n";
                head += "  return dataset;\n";
                head += "}\n";
                head += "function __auiDecorateDataset(dataset) {\n";
                head += "  if (!dataset || dataset.__auiDecoratedDataset) return dataset;\n";
                head += "  try {\n";
                head += "    Object.defineProperty(dataset, '__auiDecoratedDataset', { value: true });\n";
                head += "    Object.defineProperty(dataset, '__auiDatasetKeys', { value: {}, writable: true });\n";
                head += "    let set = dataset.set;\n";
                head += "    dataset.set = function(key, value) {\n";
                head += "      let result = set.call(dataset, key, value);\n";
                head += "      __auiSyncDatasetProperties(dataset);\n";
                head += "      return result;\n";
                head += "    };\n";
                head += "    let del = dataset.delete;\n";
                head += "    dataset.delete = function(key) {\n";
                head += "      let result = del.call(dataset, key);\n";
                head += "      if (key && dataset.__auiDatasetKeys[key]) {\n";
                head += "        delete dataset.__auiDatasetKeys[key];\n";
                head += "        try { delete dataset[key]; } catch (e) {}\n";
                head += "      }\n";
                head += "      return result;\n";
                head += "    };\n";
                head += "    __auiSyncDatasetProperties(dataset);\n";
                head += "  } catch (e) {}\n";
                head += "  return dataset;\n";
                head += "}\n";
                head += "function __auiParseQueryPairs(input) {\n";
                head += "  let raw = input == null ? '' : String(input);\n";
                head += "  if (raw.startsWith('?')) raw = raw.substring(1);\n";
                head += "  if (!raw) return [];\n";
                head += "  let parts = raw.split('&');\n";
                head += "  let out = [];\n";
                head += "  for (let i = 0; i < parts.length; i++) {\n";
                head += "    let part = parts[i];\n";
                head += "    if (!part) continue;\n";
                head += "    let idx = part.indexOf('=');\n";
                head += "    let key = idx >= 0 ? part.substring(0, idx) : part;\n";
                head += "    let value = idx >= 0 ? part.substring(idx + 1) : '';\n";
                head += "    out.push([decodeURIComponent(key.replace(/\\+/g, ' ')), decodeURIComponent(value.replace(/\\+/g, ' '))]);\n";
                head += "  }\n";
                head += "  return out;\n";
                head += "}\n";
                head += "function URLSearchParams(init) {\n";
                head += "  let pairs = [];\n";
                head += "  if (typeof init === 'string') pairs = __auiParseQueryPairs(init);\n";
                head += "  else if (init && typeof init.forEach === 'function') init.forEach((value, key) => pairs.push([String(key), String(value)]));\n";
                head += "  else if (init && typeof init.length === 'number') {\n";
                head += "    for (let i = 0; i < init.length; i++) {\n";
                head += "      let entry = init[i];\n";
                head += "      if (entry && entry.length >= 2) pairs.push([String(entry[0]), String(entry[1])]);\n";
                head += "    }\n";
                head += "  } else if (init && typeof init === 'object') {\n";
                head += "    let keys = Object.keys(init);\n";
                head += "    for (let i = 0; i < keys.length; i++) pairs.push([keys[i], String(init[keys[i]])]);\n";
                head += "  }\n";
                head += "  let api = {\n";
                head += "    append: function(key, value) { pairs.push([String(key), String(value)]); },\n";
                head += "    delete: function(key) { key = String(key); pairs = pairs.filter((entry) => entry[0] !== key); },\n";
                head += "    get: function(key) { key = String(key); for (let i = 0; i < pairs.length; i++) if (pairs[i][0] === key) return pairs[i][1]; return null; },\n";
                head += "    getAll: function(key) { key = String(key); let out = []; for (let i = 0; i < pairs.length; i++) if (pairs[i][0] === key) out.push(pairs[i][1]); return out; },\n";
                head += "    has: function(key) { key = String(key); for (let i = 0; i < pairs.length; i++) if (pairs[i][0] === key) return true; return false; },\n";
                head += "    set: function(key, value) { key = String(key); value = String(value); let next = []; let replaced = false; for (let i = 0; i < pairs.length; i++) { let entry = pairs[i]; if (entry[0] === key) { if (!replaced) { next.push([key, value]); replaced = true; } } else next.push(entry); } if (!replaced) next.push([key, value]); pairs = next; },\n";
                head += "    sort: function() { pairs.sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0); },\n";
                head += "    keys: function() { return pairs.map((entry) => entry[0]); },\n";
                head += "    values: function() { return pairs.map((entry) => entry[1]); },\n";
                head += "    entries: function() { return pairs.map((entry) => [entry[0], entry[1]]); },\n";
                head += "    forEach: function(callback, thisArg) { for (let i = 0; i < pairs.length; i++) callback.call(thisArg, pairs[i][1], pairs[i][0], api); },\n";
                head += "    toString: function() { return pairs.map((entry) => encodeURIComponent(entry[0]) + '=' + encodeURIComponent(entry[1])).join('&'); }\n";
                head += "  };\n";
                head += "  return api;\n";
                head += "}\n";
                head += "function __auiCreateLocation(href) {\n";
                head += "  let raw = href == null ? '' : String(href);\n";
                head += "  let hashIndex = raw.indexOf('#');\n";
                head += "  let hash = hashIndex >= 0 ? raw.substring(hashIndex) : '';\n";
                head += "  let hashless = hashIndex >= 0 ? raw.substring(0, hashIndex) : raw;\n";
                head += "  let queryIndex = hashless.indexOf('?');\n";
                head += "  let search = queryIndex >= 0 ? hashless.substring(queryIndex) : '';\n";
                head += "  let pathname = queryIndex >= 0 ? hashless.substring(0, queryIndex) : hashless;\n";
                head += "  let protocol = '';\n";
                head += "  let host = '';\n";
                head += "  let hostname = '';\n";
                head += "  let port = '';\n";
                head += "  let origin = '';\n";
                head += "  let schemeIndex = pathname.indexOf('://');\n";
                head += "  if (schemeIndex >= 0) {\n";
                head += "    protocol = pathname.substring(0, schemeIndex + 1);\n";
                head += "    let hostStart = schemeIndex + 3;\n";
                head += "    let slashIndex = pathname.indexOf('/', hostStart);\n";
                head += "    host = slashIndex >= 0 ? pathname.substring(hostStart, slashIndex) : pathname.substring(hostStart);\n";
                head += "    pathname = slashIndex >= 0 ? pathname.substring(slashIndex) : '/';\n";
                head += "    let colonIndex = host.indexOf(':');\n";
                head += "    hostname = colonIndex >= 0 ? host.substring(0, colonIndex) : host;\n";
                head += "    port = colonIndex >= 0 ? host.substring(colonIndex + 1) : '';\n";
                head += "    origin = protocol + '//' + host;\n";
                head += "  }\n";
                head += "  let location = {\n";
                head += "    href: raw,\n";
                head += "    protocol: protocol,\n";
                head += "    host: host,\n";
                head += "    hostname: hostname,\n";
                head += "    port: port,\n";
                head += "    origin: origin,\n";
                head += "    pathname: pathname,\n";
                head += "    search: search,\n";
                head += "    hash: hash,\n";
                head += "    assign: function() {},\n";
                head += "    replace: function() {},\n";
                head += "    reload: function() {},\n";
                head += "    toString: function() { return raw; }\n";
                head += "  };\n";
                head += "  location.searchParams = new URLSearchParams(search);\n";
                head += "  return location;\n";
                head += "}\n";
                head += "function __auiPushFormValue(entries, key, value) {\n";
                head += "  if (key == null || key === '') return;\n";
                head += "  entries.push([String(key), value == null ? '' : String(value)]);\n";
                head += "}\n";
                head += "function __auiCollectFormData(entries, form) {\n";
                head += "  if (!form || typeof form.querySelectorAll !== 'function') return;\n";
                head += "  let fields = form.querySelectorAll('input, select, textarea');\n";
                head += "  for (let i = 0; i < fields.length; i++) {\n";
                head += "    let field = fields[i];\n";
                head += "    if (!field || typeof field.getAttribute !== 'function') continue;\n";
                head += "    if (field.hasAttribute && field.hasAttribute('disabled')) continue;\n";
                head += "    let name = field.getAttribute('name');\n";
                head += "    if (!name) continue;\n";
                head += "    let tag = String(field.tagName || '').toLowerCase();\n";
                head += "    let type = String(field.getAttribute('type') || '').toLowerCase();\n";
                head += "    if (tag === 'input' && (type === 'checkbox' || type === 'radio')) {\n";
                head += "      if (field.checked) __auiPushFormValue(entries, name, field.value || 'on');\n";
                head += "      continue;\n";
                head += "    }\n";
                head += "    if (tag === 'select') {\n";
                head += "      let selected = field.selectedOptions || [];\n";
                head += "      if (selected.length > 0) {\n";
                head += "        for (let j = 0; j < selected.length; j++) __auiPushFormValue(entries, name, selected[j].value);\n";
                head += "      } else {\n";
                head += "        __auiPushFormValue(entries, name, field.value);\n";
                head += "      }\n";
                head += "      continue;\n";
                head += "    }\n";
                head += "    __auiPushFormValue(entries, name, field.value);\n";
                head += "  }\n";
                head += "}\n";
                head += "function FormData(form) {\n";
                head += "  let entries = [];\n";
                head += "  if (form) __auiCollectFormData(entries, __auiDecorateElement(form));\n";
                head += "  let api = {\n";
                head += "    append: function(key, value) { __auiPushFormValue(entries, key, value); },\n";
                head += "    delete: function(key) { key = String(key); entries = entries.filter((entry) => entry[0] !== key); },\n";
                head += "    get: function(key) { key = String(key); for (let i = 0; i < entries.length; i++) if (entries[i][0] === key) return entries[i][1]; return null; },\n";
                head += "    getAll: function(key) { key = String(key); let out = []; for (let i = 0; i < entries.length; i++) if (entries[i][0] === key) out.push(entries[i][1]); return out; },\n";
                head += "    has: function(key) { key = String(key); for (let i = 0; i < entries.length; i++) if (entries[i][0] === key) return true; return false; },\n";
                head += "    set: function(key, value) { key = String(key); value = value == null ? '' : String(value); let next = []; let replaced = false; for (let i = 0; i < entries.length; i++) { let entry = entries[i]; if (entry[0] === key) { if (!replaced) { next.push([key, value]); replaced = true; } } else next.push(entry); } if (!replaced) next.push([key, value]); entries = next; },\n";
                head += "    keys: function() { return entries.map((entry) => entry[0]); },\n";
                head += "    values: function() { return entries.map((entry) => entry[1]); },\n";
                head += "    entries: function() { return entries.map((entry) => [entry[0], entry[1]]); },\n";
                head += "    forEach: function(callback, thisArg) { for (let i = 0; i < entries.length; i++) callback.call(thisArg, entries[i][1], entries[i][0], api); },\n";
                head += "    toString: function() { return entries.map((entry) => encodeURIComponent(entry[0]) + '=' + encodeURIComponent(entry[1])).join('&'); }\n";
                head += "  };\n";
                head += "  return api;\n";
                head += "}\n";
                head += "function __auiToNode(value) {\n";
                head += "  if (value == null) return null;\n";
                head += "  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {\n";
                head += "    return __auiDecorateElement(document.createTextNode(String(value)));\n";
                head += "  }\n";
                head += "  return __auiDecorateElement(value);\n";
                head += "}\n";
                head += "function __auiAppendMany(target, args, mode) {\n";
                head += "  if (!target || !args) return null;\n";
                head += "  let last = null;\n";
                head += "  for (let i = 0; i < args.length; i++) {\n";
                head += "    let node = __auiToNode(args[i]);\n";
                head += "    if (!node) continue;\n";
                head += "    if (mode === 'prepend') target.__auiNativePrepend(node);\n";
                head += "    else if (mode === 'before') target.__auiNativeBefore(node);\n";
                head += "    else if (mode === 'after') target.__auiNativeAfter(node);\n";
                head += "    else if (mode === 'replaceWith') target.__auiNativeReplaceWith(node);\n";
                head += "    else last = target.appendChild(node);\n";
                head += "    if (mode !== 'append') last = node;\n";
                head += "  }\n";
                head += "  return __auiDecorateElement(last);\n";
                head += "}\n";
                head += "function __auiDecorateElement(el) {\n";
                head += "  if (!el || el.__auiDecoratedElement) return el;\n";
                head += "  try {\n";
                head += "    Object.defineProperty(el, '__auiDecoratedElement', { value: true });\n";
                head += "    Object.defineProperty(el, 'textContent', { get: () => el.getTextContent(), set: (v) => el.setTextContent(v == null ? '' : String(v)) });\n";
                head += "    Object.defineProperty(el, 'innerHTML', { get: () => el.getInnerHTML(), set: (v) => el.setInnerHTML(v == null ? '' : String(v)) });\n";
                head += "    Object.defineProperty(el, 'outerHTML', { get: () => el.getOuterHTML(), set: (v) => el.setOuterHTML(v == null ? '' : String(v)) });\n";
                head += "    Object.defineProperty(el, 'className', { get: () => el.getClassName(), set: (v) => el.setClassName(v == null ? '' : String(v)) });\n";
                head += "    Object.defineProperty(el, 'classList', { get: () => __auiDecorateTokenList(el.getClassList()) });\n";
                head += "    Object.defineProperty(el, 'dataset', { get: () => __auiDecorateDataset(el.getDataset()) });\n";
                head += "    Object.defineProperty(el, 'value', { get: () => el.getValue(), set: (v) => el.setValue(v == null ? '' : String(v)) });\n";
                head += "    Object.defineProperty(el, 'checked', { get: () => el.isChecked(), set: (v) => el.setChecked(!!v) });\n";
                head += "    Object.defineProperty(el, 'selectedIndex', { get: () => el.getSelectedIndex(), set: (v) => el.setSelectedIndex(v == null ? -1 : Number(v)) });\n";
                head += "    Object.defineProperty(el, 'scrollTop', { get: () => el.getScrollTop(), set: (v) => el.setScrollTop(Number(v) || 0) });\n";
                head += "    Object.defineProperty(el, 'scrollLeft', { get: () => el.getScrollLeft(), set: (v) => el.setScrollLeft(Number(v) || 0) });\n";
                head += "    Object.defineProperty(el, 'currentSrc', { get: () => el.getCurrentSrc ? el.getCurrentSrc() : '' });\n";
                head += "    Object.defineProperty(el, 'naturalWidth', { get: () => el.getNaturalWidth ? el.getNaturalWidth() : 0 });\n";
                head += "    Object.defineProperty(el, 'naturalHeight', { get: () => el.getNaturalHeight ? el.getNaturalHeight() : 0 });\n";
                head += "    Object.defineProperty(el, 'complete', { get: () => el.isComplete ? !!el.isComplete() : false });\n";
                head += "    Object.defineProperty(el, 'children', { get: () => __auiDecorateList(el.getChildren()) });\n";
                head += "    Object.defineProperty(el, 'childNodes', { get: () => __auiDecorateList(el.getChildNodes()) });\n";
                head += "    Object.defineProperty(el, 'options', { get: () => __auiDecorateList(el.getOptions()) });\n";
                head += "    Object.defineProperty(el, 'selectedOptions', { get: () => __auiDecorateList(el.getSelectedOptions()) });\n";
                head += "    Object.defineProperty(el, 'firstElementChild', { get: () => __auiDecorateElement(el.getFirstElementChild()) });\n";
                head += "    Object.defineProperty(el, 'lastElementChild', { get: () => __auiDecorateElement(el.getLastElementChild()) });\n";
                head += "    Object.defineProperty(el, 'nextElementSibling', { get: () => __auiDecorateElement(el.getNextElementSibling()) });\n";
                head += "    Object.defineProperty(el, 'previousElementSibling', { get: () => __auiDecorateElement(el.getPreviousElementSibling()) });\n";
                head += "    Object.defineProperty(el, 'parentElement', { get: () => __auiDecorateElement(el.getParentNode()) });\n";
                head += "    let qs = el.querySelector;\n";
                head += "    el.querySelector = function(sel) { return __auiDecorateElement(qs.call(el, sel)); };\n";
                head += "    let qsa = el.querySelectorAll;\n";
                head += "    el.querySelectorAll = function(sel) { return __auiDecorateList(qsa.call(el, sel)); };\n";
                head += "    let gec = el.getElementsByClassName;\n";
                head += "    el.getElementsByClassName = function(sel) { return __auiDecorateList(gec.call(el, sel)); };\n";
                head += "    let get = el.getElementsByTagName;\n";
                head += "    el.getElementsByTagName = function(sel) { return __auiDecorateList(get.call(el, sel)); };\n";
                head += "    let gen = el.getElementsByName;\n";
                head += "    el.getElementsByName = function(sel) { return __auiDecorateList(gen.call(el, sel)); };\n";
                head += "    let ac = el.appendChild;\n";
                head += "    el.appendChild = function(child) { return __auiDecorateElement(ac.call(el, child)); };\n";
                head += "    el.__auiNativePrepend = el.prepend;\n";
                head += "    el.append = function() { return __auiAppendMany(el, arguments, 'append'); };\n";
                head += "    el.prepend = function() { return __auiAppendMany(el, arguments, 'prepend'); };\n";
                head += "    let ic = el.insertBefore;\n";
                head += "    el.insertBefore = function(child, ref) { return __auiDecorateElement(ic.call(el, child, ref)); };\n";
                head += "    let rc = el.removeChild;\n";
                head += "    el.removeChild = function(child) { return __auiDecorateElement(rc.call(el, child)); };\n";
                head += "    let rm = el.remove;\n";
                head += "    el.remove = function() { return rm.call(el); };\n";
                head += "    el.__auiNativeBefore = el.before;\n";
                head += "    el.before = function() { return __auiAppendMany(el, arguments, 'before'); };\n";
                head += "    el.__auiNativeAfter = el.after;\n";
                head += "    el.after = function() { return __auiAppendMany(el, arguments, 'after'); };\n";
                head += "    el.__auiNativeReplaceWith = el.replaceWith;\n";
                head += "    el.replaceWith = function() { return __auiAppendMany(el, arguments, 'replaceWith'); };\n";
                head += "    let cc = el.closest;\n";
                head += "    el.closest = function(sel) { return __auiDecorateElement(cc.call(el, sel)); };\n";
                head += "    let gbcr = el.getBoundingClientRect;\n";
                head += "    el.getBoundingClientRect = function() { return gbcr.call(el); };\n";
                head += "    let contains = el.contains;\n";
                head += "    el.contains = function(node) { return contains.call(el, node); };\n";
                head += "    let matches = el.matches;\n";
                head += "    el.matches = function(sel) { return matches.call(el, sel); };\n";
                head += "    let focus = el.focus;\n";
                head += "    el.focus = function() { return focus.call(el); };\n";
                head += "    let blur = el.blur;\n";
                head += "    el.blur = function() { return blur.call(el); };\n";
                head += "    let click = el.click;\n";
                head += "    el.click = function() { return click.call(el); };\n";
                head += "    let submit = el.submit;\n";
                head += "    if (typeof submit === 'function') el.submit = function() { return submit.call(el); };\n";
                head += "    let scrollTo = el.scrollTo;\n";
                head += "    el.scrollTo = function(x, y) {\n";
                head += "      if (typeof x === 'object' && x) return scrollTo.call(el, Number(x.left || 0), Number(x.top || 0));\n";
                head += "      return scrollTo.call(el, Number(x) || 0, Number(y) || 0);\n";
                head += "    };\n";
                head += "    let scrollBy = el.scrollBy;\n";
                head += "    el.scrollBy = function(x, y) {\n";
                head += "      if (typeof x === 'object' && x) return scrollBy.call(el, Number(x.left || 0), Number(x.top || 0));\n";
                head += "      return scrollBy.call(el, Number(x) || 0, Number(y) || 0);\n";
                head += "    };\n";
                head += "  } catch (e) {}\n";
                head += "  return el;\n";
                head += "}\n";
                head += "function ResizeObserver(callback) {\n";
                head += "  let nativeObserver = window.createResizeObserver(function(entries) {\n";
                head += "    if (!callback) return;\n";
                head += "    callback(__auiDecorateResizeEntries(entries), observer);\n";
                head += "  });\n";
                head += "  let observer = {\n";
                head += "    observe: function(target) { nativeObserver.observe(__auiDecorateElement(target)); },\n";
                head += "    unobserve: function(target) { nativeObserver.unobserve(__auiDecorateElement(target)); },\n";
                head += "    disconnect: function() { nativeObserver.disconnect(); }\n";
                head += "  };\n";
                head += "  return observer;\n";
                head += "}\n";
                head += "function MutationObserver(callback) {\n";
                head += "  let nativeObserver = document.createMutationObserver(function(records) {\n";
                head += "    if (!callback) return;\n";
                head += "    callback(__auiDecorateMutationRecords(records), observer);\n";
                head += "  });\n";
                head += "  let observer = {\n";
                head += "    observe: function(target, options) {\n";
                head += "      options = options || {};\n";
                head += "      let filter = options.attributeFilter && options.attributeFilter.length ? options.attributeFilter.join(',') : null;\n";
                head += "      nativeObserver.observe(__auiDecorateElement(target), !!options.childList, !!options.attributes, !!options.characterData, !!options.subtree, !!options.attributeOldValue, !!options.characterDataOldValue, filter);\n";
                head += "    },\n";
                head += "    disconnect: function() { nativeObserver.disconnect(); },\n";
                head += "    takeRecords: function() { return __auiDecorateMutationRecords(nativeObserver.takeRecords()); }\n";
                head += "  };\n";
                head += "  return observer;\n";
                head += "}\n";
                head += "try {\n";
                head += "  console.debug = console.log;\n";
                head += "  let __auiLocation = __auiCreateLocation(document.getBaseURI());\n";
                head += "  Object.defineProperty(window, 'location', { get: () => __auiLocation });\n";
                head += "  Object.defineProperty(document, 'location', { get: () => __auiLocation });\n";
                head += "  let __auiDocumentQS = document.querySelector;\n";
                head += "  document.querySelector = function(sel) { return __auiDecorateElement(__auiDocumentQS.call(document, sel)); };\n";
                head += "  let __auiDocumentQSA = document.querySelectorAll;\n";
                head += "  document.querySelectorAll = function(sel) { return __auiDecorateList(__auiDocumentQSA.call(document, sel)); };\n";
                head += "  let __auiGetById = document.getElementById;\n";
                head += "  document.getElementById = function(id) { return __auiDecorateElement(__auiGetById.call(document, id)); };\n";
                head += "  let __auiCreateElement = document.createElement;\n";
                head += "  document.createElement = function(tag) { return __auiDecorateElement(__auiCreateElement.call(document, tag)); };\n";
                head += "  let __auiCreateTextNode = document.createTextNode;\n";
                head += "  document.createTextNode = function(text) { return __auiDecorateElement(__auiCreateTextNode.call(document, text)); };\n";
                head += "  let __auiCreatePath2D = document.createPath2D;\n";
                head += "  document.createPath2D = function(path) { return __auiCreatePath2D.call(document, path); };\n";
                head += "  function Path2D(path) { return document.createPath2D(path); }\n";
                head += "  let __auiDocGEC = document.getElementsByClassName;\n";
                head += "  document.getElementsByClassName = function(sel) { return __auiDecorateList(__auiDocGEC.call(document, sel)); };\n";
                head += "  let __auiDocGET = document.getElementsByTagName;\n";
                head += "  document.getElementsByTagName = function(sel) { return __auiDecorateList(__auiDocGET.call(document, sel)); };\n";
                head += "  let __auiDocGEN = document.getElementsByName;\n";
                head += "  document.getElementsByName = function(sel) { return __auiDecorateList(__auiDocGEN.call(document, sel)); };\n";
                head += "  let __auiDocAppend = document.appendChild;\n";
                head += "  document.appendChild = function(child) { return __auiDecorateElement(__auiDocAppend.call(document, child)); };\n";
                head += "  document.__auiNativePrepend = document.prepend;\n";
                head += "  document.append = function() { return __auiAppendMany(document, arguments, 'append'); };\n";
                head += "  document.prepend = function() { return __auiAppendMany(document, arguments, 'prepend'); };\n";
                head += "  let __auiFetch = fetch;\n";
                head += "  fetch = function(url) {\n";
                head += "    let p = __auiFetch(url);\n";
                head += "    let origThen = p.then;\n";
                head += "    p.then = function(onFulfilled, onRejected) {\n";
                head += "      if (!onFulfilled) return origThen.call(p, onFulfilled, onRejected);\n";
                head += "      return origThen.call(p, function(resp) { return onFulfilled(__auiDecorateResponse(resp)); }, onRejected);\n";
                head += "    };\n";
                head += "    p['catch'] = (fn) => p.catchError(fn);\n";
                head += "    return p;\n";
                head += "  };\n";
                head += "  window.scrollTo = function(x, y) {\n";
                head += "    if (typeof x === 'object' && x) return document.scrollTo(Number(x.left || 0), Number(x.top || 0));\n";
                head += "    return document.scrollTo(Number(x) || 0, Number(y) || 0);\n";
                head += "  };\n";
                head += "  window.scrollBy = function(x, y) {\n";
                head += "    if (typeof x === 'object' && x) return document.scrollBy(Number(x.left || 0), Number(x.top || 0));\n";
                head += "    return document.scrollBy(Number(x) || 0, Number(y) || 0);\n";
                head += "  };\n";
                head += "  __auiDecorateElement(document.body);\n";
                head += "} catch (e) {}\n";
                ApricityJS.eval(head + js);
            }
            fireLifecycleEvent("DOMContentLoaded", false);
            enterComplete();
            fireLifecycleEvent("load", false);
        } catch (Exception ignored) {
        }
    }

    private void beginRefreshLifecycle() {
        refreshGeneration++;
        lifecycleState = LifecycleState.LOADING;
        readyState = lifecycleState.readyStateValue;
        clearMutationObservers();
    }

    private void enterInteractive() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.INTERACTIVE;
        readyState = lifecycleState.readyStateValue;
    }

    private void enterComplete() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.COMPLETE;
        readyState = lifecycleState.readyStateValue;
    }

    private void disposeLifecycle() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.DISPOSED;
        clearMutationObservers();
        focus.clearFocus();
        focus.setPressedElement(null);
        focus.setPreviousCursorElement(null);
        lastClickTarget = null;
        lastClickButton = -1;
        lastClickTimeNs = 0L;
    }

    private void fireLifecycleEvent(String type, boolean bubbles) {
        if (body == null || type == null || type.isBlank() || !isActive()) return;
        Event event = new Event(body, type, null, false);
        event.bubbles = bubbles;
        Event.triggerSingle(event);
    }

    private void rebuildElementIndexFromBody() {
        tree.rebuildFromBody();
    }


    // 绘制队列，详见Drawer类
    public ArrayList<RenderNode> getPaintList() {
        return render.getPaintList();
    }

    // 用来将某个元素更新成另一个元素，比如创建的时候用转换成对应类的元素替换掉原来通用的
    public void updateElement(Element element) {
        tree.updateElement(element);
    }

    public Set<Element> getDirtyElements() {
        return render.getDirtyElements();
    }

    public void requestStyleRecalc(Element element) {
        if (element == null) return;
        if (element.document != this) return;
        style.requestRecalc(element);
    }

    /**
     * 统一在 tick 阶段刷新样式，避免输入事件/渲染路径反复重算 CSS。
     * <p>
     * 当前策略较保守：当某个元素的交互态（hover/active/focus）变化时，刷新该元素及其子树。
     */
    public void flushPendingStyleUpdates() {
        style.flushPendingUpdates();
    }

    /**
     * 在 Document 层统一调度“样式重算的子树递归”。
     * <p>
     * Element 只负责 recompute 自己（无递归），避免任何零散路径随手 children.forEach(...) 扩散计算量。
     */
    /**
     * 单 Document 的 tick 生命周期入口。
     * <p>
     * 关键原则：tick 做“提交与构建”，render 做“纯绘制”。
     * 因此这里负责统一执行样式刷新、元素 tick、以及 dirty flags 的 flushUpdates。
     */
    public void tickFrame() {
        if (!isActive()) return;
        commitStyleRecalc();
        stepMotion();
        tickElements();
        // tick 内可能产生新的样式失效（例如脚本写属性），再 flush 一次以保证同 tick 内一致性。
        commitStyleRecalc();
        stepMotion();
        flushMutationObservers();
        commitRenderState();
    }

    /**
     * Style Recalc 阶段：统一在 tick 中重算样式。
     */
    public void commitStyleRecalc() {
        if (!isActive()) return;
        style.flushPendingUpdates();
    }

    /**
     * Transition/Animation 阶段（占位）。
     * <p>
     * tick 阶段目前不搞 motion；推进逻辑在 render 阶段执行以保持稳定 60 帧。
     * TODO：如需让 layout 随动画变化，需要引入更严格的 commit 机制。
     */
    public void stepMotion() {
        // Intentionally no-op for now.
    }

    /**
     * Render 阶段的 motion 推进：在渲染线程、每帧执行一次，确保动画/过渡丝滑。
     * <p>
     * 该阶段只写 {@link StyleFrameCache}（当帧缓存）与少量渲染相关缓存失效（transform/filter），
     * 不去动 Document 的 dirty flags / paintList 啥的，避免 render 线程与 tick 线程职责混乱。
     */
    public void stepMotionRender() {
        if (!isActive()) return;
        motion.stepRender();
    }

    /**
     * Element Tick 阶段：滚动、输入态、逐帧逻辑。
     */
    public void tickElements() {
        if (!isActive()) return;
        render.tickElements();
    }

    /**
     * Commit Render：将 dirty flags 提交为 layout/paintList 的更新。
     */
    public void commitRenderState() {
        if (!isActive()) return;
        render.commit();
    }

    public void markDirty(int mask) {
        render.markDirty(mask);
    }

    public void markDirty(Element element, int mask) {
        render.markDirty(element, mask);
    }

    public void reapplyStylesFromCache() {
        if (body == null) return;
        body.invalidateStyle();
        markDirty(body, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    public void invalidateSelectorIndex() {
        style.invalidateSelectorIndex();
    }

    public void rebuildSelectorIndex() {
        style.rebuildSelectorIndex();
    }

    Selector.Index getSelectorIndex() {
        return style.getSelectorIndex();
    }

    ElementTree getTree() {
        return tree;
    }

    public boolean is(String path) {
        return this.path.equals(path);
    }

    public boolean is(UUID uuid) {
        return this.uuid.equals(uuid);
    }

    public String getPath() {
        return path;
    }

    public boolean isReloadPersistent() {
        return reloadPersistent;
    }

    public void setReloadPersistent(boolean reloadPersistent) {
        this.reloadPersistent = reloadPersistent;
    }

    /**
     * 每次 refresh() 递增，用于外部检测 Document 内容是否已被重建。
     */
    public long getRefreshGeneration() {
        return refreshGeneration;
    }

    public boolean isDisposed() {
        return lifecycleState == LifecycleState.DISPOSED;
    }

    public boolean isActive() {
        return lifecycleState != LifecycleState.DISPOSED;
    }

    public boolean isCurrentGeneration(long generation) {
        return isActive() && refreshGeneration == generation;
    }

    public Element createHTML(String html) {
        return HTML.createElement(this, html);
    }

    public Element createElement(String tagName) {
        return new Element(this, tagName);
    }

    public Element createTextNode(String text) {
        Element node = new Element(this, "SPAN");
        node.setTextContent(text);
        return node;
    }

    public void createRelation(Element child, Element parent, boolean head) {
        tree.createRelation(child, parent, head);
    }

    public List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(body, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(body, selector);
    }

    public void recordID(Element element) {
        tree.recordId(element);
    }

    public void removeID(String id, Element element) {
        tree.removeId(id, element);
    }

    public Element getElementById(String id) {
        return tree.getElementById(id);
    }

    public Element getDocumentElement() {
        return body;
    }

    public String getURL() {
        return path;
    }

    public String getDocumentURI() {
        return path;
    }

    public String getBaseURI() {
        return path;
    }

    public String getReadyState() {
        return readyState;
    }

    public boolean hasFocus() {
        return getFocusedElement() != null;
    }

    public void blur() {
        clearFocus();
    }

    public Element appendChild(Element element) {
        if (body == null) return null;
        return body.appendChild(element);
    }

    public void scrollTo(double x, double y) {
        if (body == null) return;
        body.scrollTo(x, y);
    }

    public void scrollBy(double x, double y) {
        if (body == null) return;
        body.scrollBy(x, y);
    }

    public Element prepend(Element element) {
        if (body == null || element == null) return null;
        body.prepend(element);
        return element;
    }

    public void addEventListener(String type, java.util.function.Consumer<Event> listener) {
        if (body == null) return;
        body.addEventListener(type, listener);
    }

    public void addEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.addEventListener(type, listener, useCapture);
    }

    public void removeEventListener(String type, java.util.function.Consumer<Event> listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.removeEventListener(type, listener, useCapture);
    }

    public boolean dispatchEvent(Object event) {
        if (!(event instanceof Event targetEvent)) return false;
        if (body == null) return false;
        if (targetEvent.target == null) targetEvent.target = body;
        if (targetEvent.currentTarget == null) targetEvent.currentTarget = body;
        return Event.tiggerEvent(targetEvent);
    }

    public List<Element> getElementsByClassName(String className) {
        if (body == null) return List.of();
        String normalized = className == null ? "" : className.trim();
        if (normalized.isEmpty()) return List.of();
        String selector = "." + String.join(".", normalized.split("\\s+"));
        return Selector.querySelectorAll(body, selector);
    }

    public List<Element> getElementsByTagName(String tagName) {
        if (body == null) return List.of();
        String normalized = tagName == null ? "" : tagName.trim();
        if (normalized.isEmpty()) return List.of();
        return Selector.querySelectorAll(body, normalized);
    }

    public List<Element> getElementsByName(String name) {
        if (body == null) return List.of();
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return List.of();
        return Selector.querySelectorAll(body, "[name=\"" + normalized + "\"]");
    }

    public static void refreshAll() {
        for (Document document : documents) {
            if (document == null || document.isReloadPersistent() || document.isDisposed()) continue;
            document.refresh();
        }
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, false);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static Document createInWorld(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, true);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static ArrayList<Document> get(String path) {
        ArrayList<Document> result = new ArrayList<>();
        for (Document document : documents) {
            if (!document.isDisposed() && document.getPath().equals(path)) result.add(document);
        }
        return result;
    }

    public static Document getByUUID(String uuid) {
        for (Document document : documents) {
            if (!document.isDisposed() && document.uuid.toString().equals(uuid)) return document;
        }
        return null;
    }

    public static List<Document> getAll() {
        return documents;
    }

    public ArrayList<Element> getElements() {
        return tree.getElements();
    }

    public static void remove(String path) {
        documents.removeIf(document -> {
            if (!document.is(path)) return false;
            document.disposeLifecycle();
            return true;
        });
    }

    public static void remove(UUID uuid) {
        documents.removeIf(document -> {
            if (!document.is(uuid)) return false;
            document.disposeLifecycle();
            return true;
        });
    }

    public void remove() {
        Document.remove(uuid);
    }

    public void removeElement(Element element) {
        tree.removeElement(element);
        motion.removeElement(element);
    }

    public MutationObserver createMutationObserver(Consumer<Object> callback) {
        MutationObserver observer = new MutationObserver(this, callback);
        if (isActive()) {
            mutationObservers.add(observer);
        } else {
            observer.disconnect();
        }
        return observer;
    }

    public CanvasPath2D createPath2D() {
        return new CanvasPath2D();
    }

    public CanvasPath2D createPath2D(Object source) {
        if (source instanceof CanvasPath2D path) return new CanvasPath2D(path);
        if (source instanceof String text) return new CanvasPath2D(text);
        return new CanvasPath2D();
    }

    public DOMMatrix createDOMMatrix() {
        return new DOMMatrix();
    }

    public DOMMatrix createDOMMatrix(Object source) {
        return new DOMMatrix(source);
    }

    public void queueMutation(MutationRecord record) {
        if (record == null || !isActive()) return;
        for (MutationObserver observer : mutationObservers) {
            if (observer != null) observer.enqueue(record);
        }
    }

    public void flushMutationObservers() {
        if (!isActive()) return;
        for (MutationObserver observer : mutationObservers) {
            if (observer == null) continue;
            observer.flush();
            if (observer.disconnected) {
                mutationObservers.remove(observer);
            }
        }
    }

    public void setTransitionActive(Element element, boolean active) {
        motion.setTransitionActive(element, active);
    }

    public void setHasAnimationSpec(Element element, boolean hasSpec) {
        motion.setHasAnimationSpec(element, hasSpec);
    }

    public Element getPreviousCursorElement() {
        return focus.getPreviousCursorElement();
    }

    public void setPreviousCursorElement(Element element) {
        focus.setPreviousCursorElement(element);
    }

    public Element getPressedElement() {
        return focus.getPressedElement();
    }

    public void setPressedElement(Element element) {
        focus.setPressedElement(element);
    }

    public boolean registerClickAndCheckDoubleClick(Element target, int button, long nowNs, long thresholdNs) {
        boolean isDoubleClick = target != null
                && lastClickTarget == target
                && lastClickButton == button
                && (nowNs - lastClickTimeNs) <= thresholdNs;
        lastClickTarget = target;
        lastClickButton = button;
        lastClickTimeNs = nowNs;
        return isDoubleClick;
    }

    public Element getActiveElement() {
        Element focused = focus.getFocusedElement();
        if (focused != null) return focused;
        return body;
    }

    public Element getFocusedElement() {
        return focus.getFocusedElement();
    }

    public void setFocusedElement(Element element) {
        focus.setFocusedElement(element);
    }


    public boolean hasAnyTextSelection() {
        return focus.hasAnyTextSelection();
    }

    public void clearAllTextSelections() {
        focus.clearAllTextSelections();
    }

    public void clearAllTextSelectionsExcept(Element keep) {
        focus.clearAllTextSelectionsExcept(keep);
    }
    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        focus.clearFocus();
    }

    private void clearMutationObservers() {
        for (MutationObserver observer : mutationObservers) {
            if (observer != null) observer.disconnect();
        }
        mutationObservers.clear();
    }

    public static final class MutationObserver {
        private final Document owner;
        private final Consumer<Object> callback;
        private final long ownerGeneration;
        private final CopyOnWriteArrayList<ObservedTarget> observed = new CopyOnWriteArrayList<>();
        private final ArrayList<MutationRecord> pending = new ArrayList<>();
        private volatile boolean disconnected = false;

        public MutationObserver(Document owner, Consumer<Object> callback) {
            this.owner = owner;
            this.callback = callback;
            this.ownerGeneration = owner == null ? -1L : owner.getRefreshGeneration();
        }

        public void observe(Element target, boolean childList, boolean attributes, boolean characterData, boolean subtree,
                            boolean attributeOldValue, boolean characterDataOldValue, String attributeFilterCsv) {
            if (target == null || disconnected) return;
            observed.removeIf(entry -> entry.target == target);
            observed.add(new ObservedTarget(
                    target,
                    childList,
                    attributes,
                    characterData,
                    subtree,
                    attributeOldValue,
                    characterDataOldValue,
                    parseAttributeFilter(attributeFilterCsv)
            ));
        }

        public void disconnect() {
            disconnected = true;
            observed.clear();
            synchronized (pending) {
                pending.clear();
            }
            owner.mutationObservers.remove(this);
        }

        public List<MutationRecord> takeRecords() {
            synchronized (pending) {
                ArrayList<MutationRecord> snapshot = new ArrayList<>(pending);
                pending.clear();
                return snapshot;
            }
        }

        void enqueue(MutationRecord record) {
            if (disconnected || record == null || owner == null || !owner.isCurrentGeneration(ownerGeneration) || !matches(record)) return;
            synchronized (pending) {
                pending.add(adapt(record));
            }
        }

        void flush() {
            if (disconnected || callback == null || owner == null || !owner.isCurrentGeneration(ownerGeneration)) return;
            List<MutationRecord> snapshot = takeRecords();
            if (snapshot.isEmpty()) return;
            callback.accept(snapshot);
        }

        private boolean matches(MutationRecord record) {
            for (ObservedTarget entry : observed) {
                if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                if (record.target == entry.target) return true;
                if (entry.subtree && entry.target.contains(record.target)) return true;
            }
            return false;
        }

        private MutationRecord adapt(MutationRecord record) {
            if ("attributes".equals(record.type) && !record.attributeName.isBlank()) {
                for (ObservedTarget entry : observed) {
                    if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                    boolean targetMatch = record.target == entry.target || (entry.subtree && entry.target.contains(record.target));
                    if (!targetMatch) continue;
                    String oldValue = entry.attributeOldValue ? record.oldValue : null;
                    return MutationRecord.attributes(record.target, record.attributeName, oldValue);
                }
            }
            if ("characterData".equals(record.type)) {
                for (ObservedTarget entry : observed) {
                    if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                    boolean targetMatch = record.target == entry.target || (entry.subtree && entry.target.contains(record.target));
                    if (!targetMatch) continue;
                    return MutationRecord.characterData(record.target, entry.characterDataOldValue ? record.oldValue : null);
                }
            }
            return record;
        }

        private static Set<String> parseAttributeFilter(String csv) {
            if (csv == null || csv.isBlank()) return Collections.emptySet();
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (String part : csv.split(",")) {
                if (part == null) continue;
                String normalized = part.trim();
                if (!normalized.isEmpty()) values.add(normalized);
            }
            return values.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(values);
        }
    }

    private record ObservedTarget(
            Element target,
            boolean childList,
            boolean attributes,
            boolean characterData,
            boolean subtree,
            boolean attributeOldValue,
            boolean characterDataOldValue,
            Set<String> attributeFilter
    ) {
        private boolean accepts(MutationRecord record) {
            if (record == null) return false;
            if ("childList".equals(record.type)) return childList;
            if ("attributes".equals(record.type)) {
                if (!attributes) return false;
                return attributeFilter == null || attributeFilter.isEmpty() || attributeFilter.contains(record.attributeName);
            }
            if ("characterData".equals(record.type)) return characterData;
            return false;
        }
    }

    public static final class MutationRecord {
        public final String type;
        public final Element target;
        public final List<Element> addedNodes;
        public final List<Element> removedNodes;
        public final Element previousSibling;
        public final Element nextSibling;
        public final String attributeName;
        public final String oldValue;

        private MutationRecord(String type, Element target, List<Element> addedNodes, List<Element> removedNodes,
                               Element previousSibling, Element nextSibling, String attributeName, String oldValue) {
            this.type = type == null ? "" : type;
            this.target = target;
            this.addedNodes = addedNodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(addedNodes));
            this.removedNodes = removedNodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(removedNodes));
            this.previousSibling = previousSibling;
            this.nextSibling = nextSibling;
            this.attributeName = attributeName == null ? "" : attributeName;
            this.oldValue = oldValue;
        }

        public static MutationRecord childList(Element target, List<Element> addedNodes, List<Element> removedNodes,
                                               Element previousSibling, Element nextSibling) {
            return new MutationRecord("childList", target, addedNodes, removedNodes, previousSibling, nextSibling, null, null);
        }

        public static MutationRecord attributes(Element target, String attributeName, String oldValue) {
            return new MutationRecord("attributes", target, List.of(), List.of(), null, null, attributeName, oldValue);
        }

        public static MutationRecord characterData(Element target, String oldValue) {
            return new MutationRecord("characterData", target, List.of(), List.of(), null, null, null, oldValue);
        }
    }
}


