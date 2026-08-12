let document = ApricityUI.getDocumentByUUID("__AUI_DOCUMENT_UUID__");
let window = ApricityUI.getWindow();
let console = window.getConsole();
let localStorage = window.getLocalStorage();
let sessionStorage = window.getSessionStorage();
let performance = window.getPerformance();
let getComputedStyle = (element) => window.getComputedStyle(element);
let fetch = (url) => {
  let p = window.fetch(url, document.getBaseURI());
  p['catch'] = (fn) => p.catchError(fn);
  return p;
};
let requestAnimationFrame = (callback) => window.requestAnimationFrame(callback);
let cancelAnimationFrame = (id) => window.cancelAnimationFrame(id);
let setTimeout = (callback, delay) => window.setTimeout(callback, delay == null ? 0 : delay);
let clearTimeout = (handle) => window.clearTimeout(handle);
let setInterval = (callback, delay) => window.setInterval(callback, delay == null ? 0 : delay);
let clearInterval = (handle) => window.clearInterval(handle);
let createImageBitmap = function(source, sx, sy, sw, sh) {
  if (arguments.length >= 5) return window.createImageBitmap(source, Number(sx), Number(sy), Number(sw), Number(sh));
  return window.createImageBitmap(source);
};
let createImageBitmapAsync = function(source, sx, sy, sw, sh) {
  let p = arguments.length >= 5
    ? window.createImageBitmapAsync(source, Number(sx), Number(sy), Number(sw), Number(sh))
    : window.createImageBitmapAsync(source);
  p['catch'] = (fn) => p.catchError(fn);
  return p;
};

function OffscreenCanvas(width, height) { return window.createOffscreenCanvas(Number(width) || 0, Number(height) || 0); }
function DOMMatrix(init) { return arguments.length === 0 ? window.createDOMMatrix() : window.createDOMMatrix(init); }

try {
  Object.defineProperty(document, 'readyState', { get: () => document.getReadyState() });
  Object.defineProperty(document, 'activeElement', { get: () => document.getActiveElement() });
  Object.defineProperty(window, 'innerWidth', { get: () => window.getInnerWidth() });
  Object.defineProperty(window, 'innerHeight', { get: () => window.getInnerHeight() });
  Object.defineProperty(window, 'devicePixelRatio', { get: () => window.getDevicePixelRatio() });
  Object.defineProperty(localStorage, 'length', { get: () => localStorage.getLength() });
  Object.defineProperty(sessionStorage, 'length', { get: () => sessionStorage.getLength() });
} catch (e) {}

// Runtime polyfills shared by every document.  Keep these in this resource so
// the Java bridge contains no embedded browser-side scripts.
var __auiInstallTextBridge = function() {
  var orig = __auiDecorateNode;
  function formatText(v) {
    if (v == null) return '';
    if (typeof v === 'string') return /^-?\d+\.0+$/.test(v) ? v.replace(/\.0+$/, '') : v;
    var n = Number(v);
    if (!isNaN(n) && isFinite(n)) return Math.floor(n) === n ? String(Math.floor(n)) : String(n);
    var s = String(v);
    return /^-?\d+\.0+$/.test(s) ? s.replace(/\.0+$/, '') : s;
  }
  function bridgeTextContent(el) {
    if (!el || el.__auiTextContentSet || typeof el.getTextContent !== 'function') return;
    try {
      Object.defineProperty(el, 'textContent', { get: function() { return el.getTextContent(); }, set: function(v) { el.setTextContent(formatText(v)); }, enumerable: true, configurable: true });
      Object.defineProperty(el, 'innerText', { get: function() { return el.getTextContent(); }, set: function(v) { el.setTextContent(formatText(v)); }, enumerable: true, configurable: true });
      el.__auiTextContentSet = true;
    } catch (e) {}
  }
  __auiDecorateNode = function(el) { el = orig(el); bridgeTextContent(el); return el; };
  try {
    var all = document.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) bridgeTextContent(all[i]);
  } catch (e) {}
};

(function() {
  var root = typeof globalThis !== 'undefined' ? globalThis : this;
  if (typeof root.window === 'undefined') root.window = root;
  function syncWindowPrompt() {
    try { if (root.window && typeof root.window.prompt !== 'function') root.window.prompt = root.prompt; } catch (e) {}
  }
  if (typeof root.prompt === 'function') { syncWindowPrompt(); return; }
  function testPromptResponse() {
    try {
      if (root.window && typeof root.window.getTestPromptResponse === 'function') {
        var windowValue = root.window.getTestPromptResponse();
        if (windowValue != null) return String(windowValue);
      }
      var systemClass = typeof Java !== 'undefined' && Java.type ? Java.type('java.lang.System') : (typeof Packages !== 'undefined' ? Packages.java.lang.System : null);
      if (systemClass) {
        var propertyValue = systemClass.getProperty('apricityui.test.promptResponse');
        if (propertyValue != null) return String(propertyValue);
        var environmentValue = systemClass.getenv('APRICITYUI_TEST_PROMPT_RESPONSE');
        if (environmentValue != null) return String(environmentValue);
      }
    } catch (e) {}
    return null;
  }
  root.prompt = function(message, defaultValue) {
    var fallback = typeof defaultValue === 'undefined' ? '' : String(defaultValue == null ? '' : defaultValue);
    var testValue = testPromptResponse();
    if (testValue != null) return testValue;
    try {
      var dialog = typeof Java !== 'undefined' && Java.type ? Java.type('javax.swing.JOptionPane') : (typeof Packages !== 'undefined' ? Packages.javax.swing.JOptionPane : null);
      if (dialog) {
        var result = dialog.showInputDialog(null, String(message == null ? '' : message), fallback);
        return result == null ? null : String(result);
      }
    } catch (e) {}
    return fallback;
  };
  syncWindowPrompt();
})();

function Event(type, init) {
  init = init || {};
  return window.createEvent(type, !!init.bubbles);
}

function CustomEvent(type, init) {
  init = init || {};
  return window.createCustomEvent(type, init.detail, !!init.bubbles);
}

function MouseEvent(type, init) {
  init = init || {};
  let x = init.clientX || 0;
  let y = init.clientY || 0;
  let button = init.button == null ? -1 : init.button;
  return window.createMouseEvent(type, x, y, button);
}

function WheelEvent(type, init) {
  init = init || {};
  let x = init.clientX || 0;
  let y = init.clientY || 0;
  let dx = Number(init.deltaX || 0);
  let dy = Number(init.deltaY || 0);
  let mode = init.deltaMode == null ? 0 : Number(init.deltaMode);
  return window.createWheelEvent(type, x, y, dx, dy, mode);
}

function PointerEvent(type, init) {
  init = init || {};
  let x = init.clientX || 0;
  let y = init.clientY || 0;
  let button = init.button == null ? -1 : Number(init.button);
  let pointerId = init.pointerId == null ? 1 : Number(init.pointerId);
  let pointerType = init.pointerType == null ? 'mouse' : String(init.pointerType);
  let isPrimary = init.isPrimary == null ? true : !!init.isPrimary;
  return window.createPointerEvent(type, x, y, button, pointerId, pointerType, isPrimary);
}

function URLSearchParams(init) {
  this.__pairs = [];
  if (typeof init === 'string') {
    let query = init.charAt(0) === '?' ? init.substring(1) : init;
    if (query.length > 0) {
      let parts = query.split('&');
      for (let i = 0; i < parts.length; i++) {
        let part = parts[i];
        if (!part) continue;
        let eq = part.indexOf('=');
        let key = eq >= 0 ? part.substring(0, eq) : part;
        let value = eq >= 0 ? part.substring(eq + 1) : '';
        this.__pairs.push([
          decodeURIComponent(key.replace(/\+/g, ' ')),
          decodeURIComponent(value.replace(/\+/g, ' '))
        ]);
      }
    }
  }
}

URLSearchParams.prototype = {
  append: function(key, value) { this.__pairs.push([String(key), String(value)]); },
  getAll: function(key) {
    key = String(key);
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) if (this.__pairs[i][0] === key) out.push(this.__pairs[i][1]);
    return out;
  },
  sort: function() { this.__pairs.sort(function(a, b) { return a[0] < b[0] ? -1 : (a[0] > b[0] ? 1 : 0); }); },
  forEach: function(callback, thisArg) {
    for (let i = 0; i < this.__pairs.length; i++) callback.call(thisArg, this.__pairs[i][1], this.__pairs[i][0], this);
  },
  toString: function() {
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) {
      out.push(encodeURIComponent(this.__pairs[i][0]) + '=' + encodeURIComponent(this.__pairs[i][1]));
    }
    return out.join('&');
  }
};

function __auiCreateLocation(href) {
  let value = String(href || '');
  let protocol = '';
  let host = '';
  let hostname = '';
  let port = '';
  let origin = '';
  let pathname = value;
  let search = '';
  let hash = '';
  let hashIndex = value.indexOf('#');
  if (hashIndex >= 0) { hash = value.substring(hashIndex); value = value.substring(0, hashIndex); }
  let searchIndex = value.indexOf('?');
  if (searchIndex >= 0) { search = value.substring(searchIndex); pathname = value.substring(0, searchIndex); } else { pathname = value; }
  let location = {
    href: href,
    protocol: protocol,
    host: host,
    hostname: hostname,
    port: port,
    origin: origin,
    pathname: pathname,
    search: search,
    hash: hash,
    assign: function() {},
    replace: function() {},
    reload: function() {}
  };
  location.searchParams = new URLSearchParams(search);
  return location;
}

function FormData(form) {
  this.__pairs = [];
  if (form && typeof form.getFormDataEntries === 'function') {
    try {
      let submitter = arguments.length > 1 ? arguments[1] : null;
      let nativeEntries = submitter == null
        ? form.getFormDataEntries()
        : form.getFormDataEntries(submitter);
      let nativeSize = nativeEntries && typeof nativeEntries.size === 'function'
        ? nativeEntries.size() : (nativeEntries && nativeEntries.length || 0);
      for (let nativeIndex = 0; nativeIndex < nativeSize; nativeIndex++) {
        let entry = typeof nativeEntries.get === 'function' ? nativeEntries.get(nativeIndex) : nativeEntries[nativeIndex];
        if (!entry) continue;
        let entryName = typeof entry.name === 'function' ? entry.name() : entry.name;
        let entryValue = typeof entry.value === 'function' ? entry.value() : entry.value;
        this.__pairs.push([String(entryName == null ? '' : entryName), String(entryValue == null ? '' : entryValue)]);
      }
      return;
    } catch (e) {}
  }
  if (form && (form.getElementsByTagName || form.querySelectorAll)) {
    let appendSelectPairs = (field, fieldName) => {
      if (!field || !fieldName) return;
      let options = field.getElementsByTagName ? field.getElementsByTagName('option') : (field.options || []);
      for (let j = 0; j < options.length; j++) {
        let option = options[j];
        if (!option) continue;
        let selected = false;
        if (typeof option.isSelected === 'function') selected = !!option.isSelected();
        else if (typeof option.hasAttribute === 'function') selected = option.hasAttribute('selected');
        else selected = !!option.selected;
        if (!selected) continue;
        if (typeof option.isOptionEffectivelyDisabled === 'function' && option.isOptionEffectivelyDisabled()) continue;
        let optionValue = typeof option.getValue === 'function' ? option.getValue() : option.value;
        if (optionValue == null && option.textContent != null) optionValue = option.textContent;
        this.__pairs.push([fieldName, optionValue == null ? '' : String(optionValue)]);
      }
    };
    let fields = [];
    let pushUniqueField = function(field) {
      if (!field) return;
      for (let index = 0; index < fields.length; index++) {
        if (fields[index] === field) return;
      }
      fields.push(field);
    };
    let inputFields = form.getElementsByTagName ? form.getElementsByTagName('input') : form.querySelectorAll('input');
    let selectFields = form.getElementsByTagName ? form.getElementsByTagName('select') : form.querySelectorAll('select');
    let textareaFields = form.getElementsByTagName ? form.getElementsByTagName('textarea') : form.querySelectorAll('textarea');
    for (let i = 0; i < inputFields.length; i++) pushUniqueField(inputFields[i]);
    for (let i = 0; i < selectFields.length; i++) pushUniqueField(selectFields[i]);
    for (let i = 0; i < textareaFields.length; i++) pushUniqueField(textareaFields[i]);
    if (typeof form.querySelector === 'function') {
      pushUniqueField(form.querySelector('input'));
      pushUniqueField(form.querySelector('select'));
      pushUniqueField(form.querySelector('textarea'));
    }
    for (let i = 0; i < fields.length; i++) {
      let field = fields[i];
      if (!field) continue;
      let fieldName = field.name;
      if ((!fieldName || fieldName === '') && typeof field.getAttribute === 'function') {
        fieldName = field.getAttribute('name');
      }
      if (!fieldName) continue;
      if (field.hasAttribute && field.hasAttribute('disabled')) continue;
      let type = (field.type || '').toLowerCase();
      if (type === 'checkbox' || type === 'radio') {
        if (field.checked) this.__pairs.push([fieldName, field.value || 'on']);
        continue;
      }
      let tagName = field.tagName;
      if ((!tagName || tagName === '') && typeof field.getNodeName === 'function') {
        tagName = field.getNodeName();
      }
      if ((field.multiple || (tagName && String(tagName).toLowerCase() === 'select'))) {
        appendSelectPairs(field, fieldName);
        continue;
      }
      this.__pairs.push([fieldName, field.value == null ? '' : String(field.value)]);
    }

    if (typeof form.querySelector === 'function') {
      let directSelect = form.querySelector('select');
      let directSelectName = directSelect && typeof directSelect.getAttribute === 'function'
        ? directSelect.getAttribute('name')
        : (directSelect ? directSelect.name : '');
      if (directSelect && directSelectName) {
        let alreadySerialized = false;
        for (let pairIndex = 0; pairIndex < this.__pairs.length; pairIndex++) {
          if (this.__pairs[pairIndex][0] === directSelectName) {
            alreadySerialized = true;
            break;
          }
        }
        if (!alreadySerialized) {
          appendSelectPairs(directSelect, directSelectName);
        }
      }
    }
  }
}

FormData.prototype = {
  append: function(key, value) { this.__pairs.push([String(key), String(value)]); },
  set: function(key, value) {
    key = String(key);
    let next = [];
    let replaced = false;
    for (let i = 0; i < this.__pairs.length; i++) {
      if (this.__pairs[i][0] !== key) next.push(this.__pairs[i]);
      else if (!replaced) { next.push([key, String(value)]); replaced = true; }
    }
    if (!replaced) next.push([key, String(value)]);
    this.__pairs = next;
  },
  delete: function(key) {
    key = String(key);
    this.__pairs = this.__pairs.filter(function(pair) { return pair[0] !== key; });
  },
  get: function(key) {
    key = String(key);
    for (let i = 0; i < this.__pairs.length; i++) if (this.__pairs[i][0] === key) return this.__pairs[i][1];
    return null;
  },
  has: function(key) {
    key = String(key);
    for (let i = 0; i < this.__pairs.length; i++) if (this.__pairs[i][0] === key) return true;
    return false;
  },
  getAll: function(key) {
    key = String(key);
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) if (this.__pairs[i][0] === key) out.push(this.__pairs[i][1]);
    return out;
  },
  forEach: function(callback, thisArg) {
    for (let i = 0; i < this.__pairs.length; i++) callback.call(thisArg, this.__pairs[i][1], this.__pairs[i][0], this);
  },
  keys: function() {
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) out.push(this.__pairs[i][0]);
    return out;
  },
  values: function() {
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) out.push(this.__pairs[i][1]);
    return out;
  },
  entries: function() { return this.__pairs.slice(); },
  toString: function() {
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) {
      out.push(encodeURIComponent(this.__pairs[i][0]) + '=' + encodeURIComponent(this.__pairs[i][1]));
    }
    return out.join('&');
  }
};

function __auiDecorateResponse(resp) {
  if (!resp || resp.__auiDecoratedResponse) return resp;
  try {
    Object.defineProperty(resp, '__auiDecoratedResponse', { value: true });
    Object.defineProperty(resp, 'ok', { get: () => resp.isOk() });
    Object.defineProperty(resp, 'status', { get: () => resp.getStatus() });
    Object.defineProperty(resp, 'url', { get: () => resp.getUrl() });
  } catch (e) {}
  return resp;
}

function __auiDefineProperty(target, name, getter, setter) {
  if (!target || !name) return false;
  try {
    let descriptor = { enumerable: true, configurable: true };
    if (getter) descriptor.get = getter;
    if (setter) descriptor.set = setter;
    Object.defineProperty(target, name, descriptor);
    return true;
  } catch (e) {
    return false;
  }
}

function __auiInstallValueBridge(target, name, getter, setter) {
  if (__auiDefineProperty(target, name, getter, setter)) return;
  try {
    target[name] = getter ? getter() : undefined;
  } catch (e) {}
}

function __auiDecorateList(list) {
  if (!list) return [];
  let out = [];
  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);
  for (let i = 0; i < size; i++) {
    let item = typeof list.get === 'function' ? list.get(i) : list[i];
    out.push(__auiDecorateElement(item));
  }
  return out;
}

function __auiDecorateCollection(list) {
  let out = __auiDecorateList(list);
  out.item = function(index) { return index >= 0 && index < out.length ? out[index] : null; };
  out.namedItem = function(name) {
    name = String(name == null ? '' : name);
    for (let i = 0; i < out.length; i++) {
      if (out[i] && (out[i].id === name || out[i].name === name)) return out[i];
    }
    return null;
  };
  return out;
}

function __auiDecorateResizeEntries(list) {
  if (!list) return [];
  let out = [];
  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);
  for (let i = 0; i < size; i++) {
    let entry = typeof list.get === 'function' ? list.get(i) : list[i];
    if (!entry) continue;
    let rect = entry.contentRect;
    out.push({
      target: __auiDecorateElement(entry.target),
      contentRect: rect,
      borderBoxSize: [{ inlineSize: rect.borderBoxWidth, blockSize: rect.borderBoxHeight }],
      contentBoxSize: [{ inlineSize: rect.width, blockSize: rect.height }]
    });
  }
  return out;
}

function __auiDecorateMutationRecords(list) {
  if (!list) return [];
  let out = [];
  let size = typeof list.size === 'function' ? list.size() : (list.length || 0);
  for (let i = 0; i < size; i++) {
    let record = typeof list.get === 'function' ? list.get(i) : list[i];
    if (!record) continue;
    out.push({
      type: record.type,
      target: __auiDecorateElement(record.target),
      addedNodes: __auiDecorateList(record.addedNodes),
      removedNodes: __auiDecorateList(record.removedNodes),
      previousSibling: __auiDecorateElement(record.previousSibling),
      nextSibling: __auiDecorateElement(record.nextSibling),
      attributeName: record.attributeName,
      oldValue: record.oldValue
    });
  }
  return out;
}

function __auiDecorateTokenList(list) {
  if (!list || list.__auiDecoratedTokenList) return list;
  try {
    Object.defineProperty(list, '__auiDecoratedTokenList', { value: true });
    Object.defineProperty(list, 'length', { get: () => list.getLength() });
    if (typeof list.add === 'function') {
      let add = list.add;
      list.add = function() { return add.apply(list, arguments); };
    }
    if (typeof list.remove === 'function') {
      let remove = list.remove;
      list.remove = function() { return remove.apply(list, arguments); };
    }
  } catch (e) {}
  return list;
}

function __auiSyncDatasetProperties(dataset) {
  if (!dataset || typeof dataset.keys !== 'function') return dataset;
  let keys = dataset.keys();
  let size = typeof keys.size === 'function' ? keys.size() : (keys.length || 0);
  for (let i = 0; i < size; i++) {
    let key = typeof keys.get === 'function' ? keys.get(i) : keys[i];
    if (!key || dataset.__auiDatasetKeys[key]) continue;
    dataset.__auiDatasetKeys[key] = true;
    try {
      Object.defineProperty(dataset, key, {
        get: () => dataset.get(key),
        set: (value) => dataset.set(key, value == null ? '' : String(value)),
        enumerable: true,
        configurable: true
      });
    } catch (e) {}
  }
  return dataset;
}

function __auiDecorateDataset(dataset) {
  if (!dataset || dataset.__auiDecoratedDataset) return dataset;
  try {
    Object.defineProperty(dataset, '__auiDecoratedDataset', { value: true });
    Object.defineProperty(dataset, '__auiDatasetKeys', { value: {}, writable: true });
    let set = dataset.set;
    dataset.set = function(key, value) {
      let result = set.call(dataset, key, value);
      __auiSyncDatasetProperties(dataset);
      return result;
    };
    let del = dataset.delete;
    dataset.delete = function(key) {
      let result = del.call(dataset, key);
      if (key && dataset.__auiDatasetKeys[key]) {
        delete dataset.__auiDatasetKeys[key];
        try { delete dataset[key]; } catch (e) {}
      }
      return result;
    };
    __auiSyncDatasetProperties(dataset);
  } catch (e) {}
  return dataset;
}

function __auiToNode(value) {
  if (value == null) return null;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return __auiDecorateNode(document.createTextNode(String(value)));
  }
  return __auiDecorateNode(value);
}

function __auiAppendMany(target, args, mode) {
  if (!target || !args) return null;
  let last = null;
  for (let i = 0; i < args.length; i++) {
    let node = __auiToNode(args[i]);
    if (!node) continue;
    if (mode === 'prepend') target.__auiNativePrepend(node);
    else if (mode === 'before') target.__auiNativeBefore(node);
    else if (mode === 'after') target.__auiNativeAfter(node);
    else if (mode === 'replaceWith') target.__auiNativeReplaceWith(node);
    else last = target.appendChild(node);
    if (mode !== 'append') last = node;
  }
  return __auiDecorateNode(last);
}

function __auiDecorateNode(el) {
  if (!el || el.__auiDecoratedElement) return el;
  try {
    Object.defineProperty(el, '__auiDecoratedElement', { value: true });
    __auiInstallValueBridge(el, 'nodeType', () => el.getNodeType());
    __auiInstallValueBridge(el, 'nodeName', () => el.getNodeName());
    __auiInstallValueBridge(el, 'nodeValue', () => el.getNodeValue ? el.getNodeValue() : null, (v) => {
      if (typeof el.setTextContent === 'function') el.setTextContent(v == null ? '' : String(v));
    });
    __auiInstallValueBridge(el, 'textContent', () => el.getTextContent(), (v) => el.setTextContent(v == null ? '' : String(v)));
    __auiInstallValueBridge(el, 'childNodes', () => __auiDecorateList(el.getChildNodes()));
    __auiInstallValueBridge(el, 'firstChild', () => __auiDecorateNode(el.getFirstChild ? el.getFirstChild() : null));
    __auiInstallValueBridge(el, 'lastChild', () => __auiDecorateNode(el.getLastChild ? el.getLastChild() : null));
    __auiInstallValueBridge(el, 'nextSibling', () => __auiDecorateNode(el.getNextSibling ? el.getNextSibling() : null));
    __auiInstallValueBridge(el, 'previousSibling', () => __auiDecorateNode(el.getPreviousSibling ? el.getPreviousSibling() : null));
    __auiInstallValueBridge(el, 'parentNode', () => __auiDecorateNode(el.getParentNode ? el.getParentNode() : null));
    __auiInstallValueBridge(el, 'ownerDocument', () => el.getOwnerDocument ? el.getOwnerDocument() : null);
    __auiInstallValueBridge(el, 'isConnected', () => el.isConnected ? !!el.isConnected() : false);
    __auiInstallValueBridge(el, 'data', () => el.getNodeValue ? el.getNodeValue() : null, (v) => {
      if (typeof el.setTextContent === 'function') el.setTextContent(v == null ? '' : String(v));
    });
    let ac = el.appendChild;
    el.appendChild = function(child) { return __auiDecorateNode(ac.call(el, child)); };
    el.__auiNativePrepend = el.prepend;
    el.append = function() { return __auiAppendMany(el, arguments, 'append'); };
    el.prepend = function() { return __auiAppendMany(el, arguments, 'prepend'); };
    let ic = el.insertBefore;
    el.insertBefore = function(child, ref) { return __auiDecorateNode(ic.call(el, child, ref)); };
    let rc = el.removeChild;
    el.removeChild = function(child) { return __auiDecorateNode(rc.call(el, child)); };
    let rm = el.remove;
    el.remove = function() { return rm.call(el); };
    el.__auiNativeBefore = el.before;
    el.before = function() { return __auiAppendMany(el, arguments, 'before'); };
    el.__auiNativeAfter = el.after;
    el.after = function() { return __auiAppendMany(el, arguments, 'after'); };
    el.__auiNativeReplaceWith = el.replaceWith;
    el.replaceWith = function() { return __auiAppendMany(el, arguments, 'replaceWith'); };
    let contains = el.contains;
    el.contains = function(node) { return contains.call(el, node); };
    if (typeof el.getClassName === 'function') {
      __auiInstallValueBridge(el, 'innerHTML', () => el.getInnerHTML(), (v) => el.setInnerHTML(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'outerHTML', () => el.getOuterHTML(), (v) => el.setOuterHTML(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'className', () => el.getClassName(), (v) => el.setClassName(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'classList', () => __auiDecorateTokenList(el.getClassList()));
      __auiInstallValueBridge(el, 'dataset', () => __auiDecorateDataset(el.getDataset()));
      __auiInstallValueBridge(el, 'name', () => el.getAttribute('name'), (v) => el.setAttribute('name', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'type', () => el.getType(), (v) => el.setType(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'form', () => __auiDecorateElement(el.getForm ? el.getForm() : null));
      __auiInstallValueBridge(el, 'disabled', () => !!el.isDisabled(), (v) => el.setDisabled(!!v));
      __auiInstallValueBridge(el, 'contentEditable', () => el.isContentEditable ? (el.isContentEditable() ? 'true' : 'false') : 'false', (v) => {
              var next = v == null ? '' : String(v);
              if (next === 'false' || next === 'inherit') el.removeAttribute('contenteditable');
              else el.setAttribute('contenteditable', 'true');
            });

      __auiInstallValueBridge(el, 'multiple', () => !!el.hasAttribute('multiple'), (v) => el.toggleAttribute('multiple', !!v));
      __auiInstallValueBridge(el, 'value', () => el.getValue(), (v) => el.setValue(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'defaultValue', () => el.getDefaultValue ? el.getDefaultValue() : el.getAttribute('value'),
        (v) => { if (el.setDefaultValue) el.setDefaultValue(v == null ? '' : String(v)); });
      __auiInstallValueBridge(el, 'defaultChecked', () => !!(el.isDefaultChecked && el.isDefaultChecked()),
        (v) => { if (el.setDefaultChecked) el.setDefaultChecked(!!v); });
      __auiInstallValueBridge(el, 'required', () => !!el.hasAttribute('required'), (v) => el.toggleAttribute('required', !!v));
      __auiInstallValueBridge(el, 'readOnly', () => !!el.hasAttribute('readonly'), (v) => el.toggleAttribute('readonly', !!v));
      __auiInstallValueBridge(el, 'pattern', () => el.getAttribute('pattern'), (v) => el.setAttribute('pattern', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'min', () => el.getAttribute('min'), (v) => el.setAttribute('min', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'max', () => el.getAttribute('max'), (v) => el.setAttribute('max', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'step', () => el.getAttribute('step'), (v) => el.setAttribute('step', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'minLength', () => Number(el.getAttribute('minlength') || -1), (v) => el.setAttribute('minlength', String(Number(v))));
      __auiInstallValueBridge(el, 'maxLength', () => Number(el.getAttribute('maxlength') || -1), (v) => el.setAttribute('maxlength', String(Number(v))));
      __auiInstallValueBridge(el, 'placeholder', () => el.getPlaceholder ? el.getPlaceholder() : el.getAttribute('placeholder'),
        (v) => { if (el.setPlaceholder) el.setPlaceholder(v == null ? '' : String(v)); });
      __auiInstallValueBridge(el, 'accept', () => el.getAttribute('accept'), (v) => el.setAttribute('accept', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'autocomplete', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        return el.getAttribute('autocomplete') || (nodeName === 'FORM' ? 'on' : '');
      }, (v) => el.setAttribute('autocomplete', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'inputMode', () => el.getAttribute('inputmode'), (v) => el.setAttribute('inputmode', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'formNoValidate', () => !!el.hasAttribute('formnovalidate'), (v) => el.toggleAttribute('formnovalidate', !!v));
      __auiInstallValueBridge(el, 'noValidate', () => !!el.hasAttribute('novalidate'), (v) => el.toggleAttribute('novalidate', !!v));
      __auiInstallValueBridge(el, 'action', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        if (nodeName !== 'FORM') return el.getAttribute('action');
        return el.getAttribute('action') || (document.getBaseURI ? document.getBaseURI() : '');
      }, (v) => el.setAttribute('action', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'method', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        return nodeName === 'FORM' ? (el.getAttribute('method') || 'get').toLowerCase() : el.getAttribute('method');
      }, (v) => el.setAttribute('method', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'target', () => el.getAttribute('target'), (v) => el.setAttribute('target', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'enctype', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        return nodeName === 'FORM'
          ? (el.getAttribute('enctype') || 'application/x-www-form-urlencoded')
          : el.getAttribute('enctype');
      }, (v) => el.setAttribute('enctype', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'encoding', () => el.getAttribute('enctype') || 'application/x-www-form-urlencoded',
        (v) => el.setAttribute('enctype', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'acceptCharset', () => el.getAttribute('accept-charset') || 'UTF-8',
        (v) => el.setAttribute('accept-charset', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'formAction', () => el.getAttribute('formaction'), (v) => el.setAttribute('formaction', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'formMethod', () => el.getAttribute('formmethod'), (v) => el.setAttribute('formmethod', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'formTarget', () => el.getAttribute('formtarget'), (v) => el.setAttribute('formtarget', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'formEnctype', () => el.getAttribute('formenctype'), (v) => el.setAttribute('formenctype', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'rows', () => Number(el.getAttribute('rows') || 2), (v) => el.setAttribute('rows', String(Number(v) || 0)));
      __auiInstallValueBridge(el, 'cols', () => Number(el.getAttribute('cols') || 20), (v) => el.setAttribute('cols', String(Number(v) || 0)));
      __auiInstallValueBridge(el, 'wrap', () => el.getAttribute('wrap') || 'soft', (v) => el.setAttribute('wrap', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'list', () => {
        let id = el.getAttribute('list');
        return id && typeof document.getElementById === 'function' ? __auiDecorateElement(document.getElementById(id)) : null;
      }, (v) => el.setAttribute('list', v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'checked', () => el.isChecked(), (v) => el.setChecked(!!v));
      __auiInstallValueBridge(el, 'selected', () => el.isSelected(), (v) => el.setSelected(!!v));
      __auiInstallValueBridge(el, 'defaultSelected', () => el.isDefaultSelected(), (v) => el.setDefaultSelected(!!v));
      __auiInstallValueBridge(el, 'label', () => el.getOptionLabel(), (v) => el.setOptionLabel(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'text', () => el.getOptionText(), (v) => el.setOptionText(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'index', () => el.getOptionIndex());
      __auiInstallValueBridge(el, 'selectedIndex', () => el.getSelectedIndex(), (v) => el.setSelectedIndex(v == null ? -1 : Number(v)));
      __auiInstallValueBridge(el, 'length', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        return nodeName === 'FORM' && el.getFormLength ? el.getFormLength() : el.getSelectLength();
      });
      __auiInstallValueBridge(el, 'size', () => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        return nodeName === 'SELECT' ? el.getSelectSize() : Number(el.getAttribute('size') || 20);
      }, (v) => {
        let nodeName = el.getNodeName ? String(el.getNodeName()).toUpperCase() : '';
        if (nodeName === 'SELECT') el.setSelectSize(v == null ? 0 : Number(v));
        else el.setAttribute('size', String(Number(v) || 0));
      });
      __auiInstallValueBridge(el, 'scrollTop', () => el.getScrollTop(), (v) => el.setScrollTop(Number(v) || 0));
      __auiInstallValueBridge(el, 'scrollLeft', () => el.getScrollLeft(), (v) => el.setScrollLeft(Number(v) || 0));
      __auiInstallValueBridge(el, 'currentSrc', () => el.getCurrentSrc ? el.getCurrentSrc() : '');
      __auiInstallValueBridge(el, 'naturalWidth', () => el.getNaturalWidth ? el.getNaturalWidth() : 0);
      __auiInstallValueBridge(el, 'naturalHeight', () => el.getNaturalHeight ? el.getNaturalHeight() : 0);
      __auiInstallValueBridge(el, 'complete', () => el.isComplete ? !!el.isComplete() : false);
      __auiInstallValueBridge(el, 'children', () => __auiDecorateList(el.getChildren()));
      __auiInstallValueBridge(el, 'elements', () => __auiDecorateCollection(el.getFormControls ? el.getFormControls() : []));
      __auiInstallValueBridge(el, 'options', () => __auiDecorateCollection(el.getOptions()));
      __auiInstallValueBridge(el, 'selectedOptions', () => __auiDecorateCollection(el.getSelectedOptions()));
      __auiInstallValueBridge(el, 'labels', () => __auiDecorateList(el.getLabels ? el.getLabels() : []));
      __auiInstallValueBridge(el, 'willValidate', () => !!(el.isWillValidate && el.isWillValidate()));
      __auiInstallValueBridge(el, 'validationMessage', () => el.getValidationMessage ? el.getValidationMessage() : '');
      __auiInstallValueBridge(el, 'validity', () => {
        let state = el.getValidity ? el.getValidity() : null;
        if (!state) return { valid: true };
        return {
          badInput: !!state.badInput, customError: !!state.customError,
          patternMismatch: !!state.patternMismatch, rangeOverflow: !!state.rangeOverflow,
          rangeUnderflow: !!state.rangeUnderflow, stepMismatch: !!state.stepMismatch,
          tooLong: !!state.tooLong, tooShort: !!state.tooShort,
          typeMismatch: !!state.typeMismatch, valueMissing: !!state.valueMissing,
          valid: !!state.valid
        };
      });
      __auiInstallValueBridge(el, 'valueAsNumber', () => el.getValueAsNumber ? el.getValueAsNumber() : NaN,
        (v) => { if (el.setValueAsNumber) el.setValueAsNumber(Number(v)); });
      __auiInstallValueBridge(el, 'selectionStart', () => el.getSelectionStart ? el.getSelectionStart() : null,
        (v) => { if (el.setSelectionRange) el.setSelectionRange(Number(v), el.getSelectionEnd ? el.getSelectionEnd() : Number(v)); });
      __auiInstallValueBridge(el, 'selectionEnd', () => el.getSelectionEnd ? el.getSelectionEnd() : null,
        (v) => { if (el.setSelectionRange) el.setSelectionRange(el.getSelectionStart ? el.getSelectionStart() : Number(v), Number(v)); });
      __auiInstallValueBridge(el, 'selectionDirection', () => el.getSelectionDirection ? el.getSelectionDirection() : 'none');
      __auiInstallValueBridge(el, 'files', () => {
        let values = el.getFileList ? el.getFileList() : [];
        let raw = __auiDecorateList(values);
        let out = [];
        for (let fileIndex = 0; fileIndex < raw.length; fileIndex++) {
          let path = String(raw[fileIndex] == null ? '' : raw[fileIndex]);
          let slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
          let name = slash < 0 ? path : path.substring(slash + 1);
          let dot = name.lastIndexOf('.');
          let extension = dot >= 0 ? name.substring(dot + 1).toLowerCase() : '';
          let type = extension === 'html' || extension === 'htm' ? 'text/html'
            : extension === 'json' ? 'application/json'
            : extension === 'png' ? 'image/png'
            : extension === 'jpg' || extension === 'jpeg' ? 'image/jpeg'
            : extension === 'gif' ? 'image/gif' : '';
          out.push({ name: name, type: type, size: 0, lastModified: 0, path: path });
        }
        out.item = function(index) { return index >= 0 && index < out.length ? out[index] : null; };
        return out;
      });
      __auiInstallValueBridge(el, 'firstElementChild', () => __auiDecorateNode(el.getFirstElementChild()));
      __auiInstallValueBridge(el, 'lastElementChild', () => __auiDecorateNode(el.getLastElementChild()));
      __auiInstallValueBridge(el, 'nextElementSibling', () => __auiDecorateNode(el.getNextElementSibling()));
      __auiInstallValueBridge(el, 'previousElementSibling', () => __auiDecorateNode(el.getPreviousElementSibling()));
      __auiInstallValueBridge(el, 'parentElement', () => __auiDecorateNode(el.getParentNode()));
      let qs = el.querySelector;
      el.querySelector = function(sel) { return __auiDecorateNode(qs.call(el, sel)); };
      let qsa = el.querySelectorAll;
      el.querySelectorAll = function(sel) { return __auiDecorateList(qsa.call(el, sel)); };
      let gec = el.getElementsByClassName;
      el.getElementsByClassName = function(sel) { return __auiDecorateList(gec.call(el, sel)); };
      let get = el.getElementsByTagName;
      el.getElementsByTagName = function(sel) { return __auiDecorateList(get.call(el, sel)); };
      let gen = el.getElementsByName;
      el.getElementsByName = function(sel) { return __auiDecorateList(gen.call(el, sel)); };
      let cc = el.closest;
      el.closest = function(sel) { return __auiDecorateNode(cc.call(el, sel)); };
      let gbcr = el.getBoundingClientRect;
      el.getBoundingClientRect = function() { return gbcr.call(el); };
      let matches = el.matches;
      el.matches = function(sel) { return matches.call(el, sel); };
      let focus = el.focus;
      el.focus = function() { return focus.call(el); };
      let blur = el.blur;
      el.blur = function() { return blur.call(el); };
      let click = el.click;
      el.click = function() { return click.call(el); };
      let submit = el.submit;
      if (typeof submit === 'function') el.submit = function() { return submit.call(el); };
      let requestSubmit = el.requestSubmit;
      if (typeof requestSubmit === 'function') el.requestSubmit = function(submitter) {
        return submitter == null ? requestSubmit.call(el) : requestSubmit.call(el, submitter);
      };
      let reset = el.reset;
      if (typeof reset === 'function') el.reset = function() { return reset.call(el); };
      let checkValidity = el.checkValidity;
      if (typeof checkValidity === 'function') el.checkValidity = function() { return checkValidity.call(el); };
      let reportValidity = el.reportValidity;
      if (typeof reportValidity === 'function') el.reportValidity = function() { return reportValidity.call(el); };
      let setCustomValidity = el.setCustomValidity;
      if (typeof setCustomValidity === 'function') el.setCustomValidity = function(message) {
        return setCustomValidity.call(el, message == null ? '' : String(message));
      };
      let selectText = el.select;
      if (typeof selectText === 'function') el.select = function() { return selectText.call(el); };
      let setSelectionRange = el.setSelectionRange;
      if (typeof setSelectionRange === 'function') el.setSelectionRange = function(start, end, direction) {
        return setSelectionRange.call(el, Number(start) || 0, Number(end) || 0, direction == null ? 'none' : String(direction));
      };
      let setRangeText = el.setRangeText;
      if (typeof setRangeText === 'function') el.setRangeText = function(value, start, end, mode) {
        if (arguments.length < 2) return setRangeText.call(el, value == null ? '' : String(value));
        return setRangeText.call(el, value == null ? '' : String(value), Number(start) || 0, Number(end) || 0, mode == null ? 'preserve' : String(mode));
      };
      let stepUp = el.stepUp;
      if (typeof stepUp === 'function') el.stepUp = function(count) {
        return arguments.length ? stepUp.call(el, Number(count) || 0) : stepUp.call(el);
      };
      let stepDown = el.stepDown;
      if (typeof stepDown === 'function') el.stepDown = function(count) {
        return arguments.length ? stepDown.call(el, Number(count) || 0) : stepDown.call(el);
      };
      let beginComposition = el.beginComposition;
      if (typeof beginComposition === 'function') el.beginComposition = function(data) {
        return beginComposition.call(el, data == null ? '' : String(data));
      };
      let updateComposition = el.updateComposition;
      if (typeof updateComposition === 'function') el.updateComposition = function(data) {
        return updateComposition.call(el, data == null ? '' : String(data));
      };
      let endComposition = el.endComposition;
      if (typeof endComposition === 'function') el.endComposition = function(data) {
        return endComposition.call(el, data == null ? '' : String(data));
      };
      let scrollTo = el.scrollTo;
      el.scrollTo = function(x, y) {
        if (typeof x === 'object' && x) return scrollTo.call(el, Number(x.left || 0), Number(x.top || 0));
        return scrollTo.call(el, Number(x) || 0, Number(y) || 0);
      };
      let scrollBy = el.scrollBy;
      el.scrollBy = function(x, y) {
        if (typeof x === 'object' && x) return scrollBy.call(el, Number(x.left || 0), Number(x.top || 0));
        return scrollBy.call(el, Number(x) || 0, Number(y) || 0);
      };
    }
  } catch (e) {}
  return el;
}

function __auiDecorateElement(el) {
  return __auiDecorateNode(el);
}

function ResizeObserver(callback) {
  let nativeObserver = window.createResizeObserver(function(entries) {
    if (!callback) return;
    callback(__auiDecorateResizeEntries(entries), observer);
  });
  let observer = {
    observe: function(target) { nativeObserver.observe(__auiDecorateElement(target)); },
    unobserve: function(target) { nativeObserver.unobserve(__auiDecorateElement(target)); },
    disconnect: function() { nativeObserver.disconnect(); }
  };
  return observer;
}

function MutationObserver(callback) {
  let nativeObserver = document.createMutationObserver(function(records) {
    if (!callback) return;
    callback(__auiDecorateMutationRecords(records), observer);
  });
  let observer = {
    observe: function(target, options) {
      options = options || {};
      let filter = options.attributeFilter && options.attributeFilter.length ? options.attributeFilter.join(',') : null;
      nativeObserver.observe(__auiDecorateElement(target), !!options.childList, !!options.attributes, !!options.characterData, !!options.subtree, !!options.attributeOldValue, !!options.characterDataOldValue, filter);
    },
    disconnect: function() { nativeObserver.disconnect(); },
    takeRecords: function() { return __auiDecorateMutationRecords(nativeObserver.takeRecords()); }
  };
  return observer;
}

try {
  console.debug = console.log;
  let __auiLocation = __auiCreateLocation(document.getBaseURI());
  __auiInstallValueBridge(window, 'location', () => __auiLocation);
  __auiInstallValueBridge(document, 'location', () => __auiLocation);
  try { globalThis.location = __auiLocation; } catch (e) {}
  let __auiDocumentQS = document.querySelector;
  document.querySelector = function(sel) { return __auiDecorateElement(__auiDocumentQS.call(document, sel)); };
  let __auiDocumentQSA = document.querySelectorAll;
  document.querySelectorAll = function(sel) { return __auiDecorateList(__auiDocumentQSA.call(document, sel)); };
  let __auiGetById = document.getElementById;
  document.getElementById = function(id) { return __auiDecorateElement(__auiGetById.call(document, id)); };
  let __auiCreateElement = document.createElement;
  document.createElement = function(tag) { return __auiDecorateElement(__auiCreateElement.call(document, tag)); };
  let __auiCreateTextNode = document.createTextNode;
  document.createTextNode = function(text) { return __auiDecorateElement(__auiCreateTextNode.call(document, text)); };
  let __auiCreateComment = document.createComment;
  if (typeof __auiCreateComment === 'function') {
    document.createComment = function(text) { return __auiDecorateElement(__auiCreateComment.call(document, text)); };
  }
  let __auiCreatePath2D = document.createPath2D;
  document.createPath2D = function(path) { return __auiCreatePath2D.call(document, path); };
  function Path2D(path) { return document.createPath2D(path); }
  let __auiDocGEC = document.getElementsByClassName;
  document.getElementsByClassName = function(sel) { return __auiDecorateList(__auiDocGEC.call(document, sel)); };
  let __auiDocGET = document.getElementsByTagName;
  document.getElementsByTagName = function(sel) { return __auiDecorateList(__auiDocGET.call(document, sel)); };
  let __auiDocGEN = document.getElementsByName;
  document.getElementsByName = function(sel) { return __auiDecorateList(__auiDocGEN.call(document, sel)); };
  let __auiDocAppend = document.appendChild;
  document.appendChild = function(child) { return __auiDecorateElement(__auiDocAppend.call(document, child)); };
  document.__auiNativePrepend = document.prepend;
  document.append = function() { return __auiAppendMany(document, arguments, 'append'); };
  document.prepend = function() { return __auiAppendMany(document, arguments, 'prepend'); };
  let __auiFetch = fetch;
  fetch = function(url) {
    let p = __auiFetch(url);
    let origThen = p.then;
    p.then = function(onFulfilled, onRejected) {
      if (!onFulfilled) return origThen.call(p, onFulfilled, onRejected);
      return origThen.call(p, function(resp) { return onFulfilled(__auiDecorateResponse(resp)); }, onRejected);
    };
    p['catch'] = (fn) => p.catchError(fn);
    return p;
  };
  window.scrollTo = function(x, y) {
    if (typeof x === 'object' && x) return document.scrollTo(Number(x.left || 0), Number(x.top || 0));
    return document.scrollTo(Number(x) || 0, Number(y) || 0);
  };
  window.scrollBy = function(x, y) {
    if (typeof x === 'object' && x) return document.scrollBy(Number(x.left || 0), Number(x.top || 0));
    return document.scrollBy(Number(x) || 0, Number(y) || 0);
  };
  let __auiCreateFragment = document.createDocumentFragment;
  if (typeof __auiCreateFragment === 'function') {
    document.createDocumentFragment = function() { return __auiDecorateNode(__auiCreateFragment.call(document)); };
  }
  let __auiGetSelection = window.getSelection;
  if (typeof __auiGetSelection === 'function') {
    window.getSelection = function() {
      let sel = __auiGetSelection.call(window);
      if (sel && !sel.__auiDecoratedSelection) {
        try {
          Object.defineProperty(sel, '__auiDecoratedSelection', { value: true });
          Object.defineProperty(sel, 'anchorNode', {
            get: function() { return __auiDecorateNode(sel.getAnchorNode()); },
            enumerable: true, configurable: true
          });
          Object.defineProperty(sel, 'focusNode', {
            get: function() { return __auiDecorateNode(sel.getFocusNode()); },
            enumerable: true, configurable: true
          });
        } catch (e) {}
      }
      return sel;
    };
  }
  let __auiCreateRange = document.createRange;
  if (typeof __auiCreateRange === 'function') {
    document.createRange = function() { return __auiCreateRange.call(document); };
  }
  let __auiCreateTreeWalker = document.createTreeWalker;
  if (typeof __auiCreateTreeWalker === 'function') {
    document.createTreeWalker = function(root, whatToShow) {
      return __auiCreateTreeWalker.call(document, root, whatToShow == null ? 0xFFFFFFFF : whatToShow);
    };
  }
  __auiDecorateElement(document.body);
} catch (e) {}
// Node/NodeFilter 常量独立定义:放在 try 块之外,保证任何桥包装失败都不影响
// 页面脚本使用 Node.ELEMENT_NODE / NodeFilter.SHOW_TEXT(浏览器全局常量)。
// 注意:此 Rhino fork 无 globalThis,直接声明为顶层 var(与页面脚本同一 top-level scope)。
var Node = { ELEMENT_NODE: 1, ATTRIBUTE_NODE: 2, TEXT_NODE: 3, CDATA_SECTION_NODE: 4,
             COMMENT_NODE: 8, DOCUMENT_NODE: 9, DOCUMENT_FRAGMENT_NODE: 11 };
var NodeFilter = { SHOW_ALL: 0xFFFFFFFF, SHOW_TEXT: 4 };
__auiInstallTextBridge();
