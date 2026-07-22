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
  getAll: function(key) {
    key = String(key);
    let out = [];
    for (let i = 0; i < this.__pairs.length; i++) if (this.__pairs[i][0] === key) out.push(this.__pairs[i][1]);
    return out;
  },
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
      __auiInstallValueBridge(el, 'disabled', () => !!el.isDisabled(), (v) => el.setDisabled(!!v));
      __auiInstallValueBridge(el, 'multiple', () => !!el.hasAttribute('multiple'), (v) => el.toggleAttribute('multiple', !!v));
      __auiInstallValueBridge(el, 'value', () => el.getValue(), (v) => el.setValue(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'checked', () => el.isChecked(), (v) => el.setChecked(!!v));
      __auiInstallValueBridge(el, 'selected', () => el.isSelected(), (v) => el.setSelected(!!v));
      __auiInstallValueBridge(el, 'defaultSelected', () => el.isDefaultSelected(), (v) => el.setDefaultSelected(!!v));
      __auiInstallValueBridge(el, 'label', () => el.getOptionLabel(), (v) => el.setOptionLabel(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'text', () => el.getOptionText(), (v) => el.setOptionText(v == null ? '' : String(v)));
      __auiInstallValueBridge(el, 'index', () => el.getOptionIndex());
      __auiInstallValueBridge(el, 'selectedIndex', () => el.getSelectedIndex(), (v) => el.setSelectedIndex(v == null ? -1 : Number(v)));
      __auiInstallValueBridge(el, 'length', () => el.getSelectLength());
      __auiInstallValueBridge(el, 'size', () => el.getSelectSize(), (v) => el.setSelectSize(v == null ? 0 : Number(v)));
      __auiInstallValueBridge(el, 'scrollTop', () => el.getScrollTop(), (v) => el.setScrollTop(Number(v) || 0));
      __auiInstallValueBridge(el, 'scrollLeft', () => el.getScrollLeft(), (v) => el.setScrollLeft(Number(v) || 0));
      __auiInstallValueBridge(el, 'currentSrc', () => el.getCurrentSrc ? el.getCurrentSrc() : '');
      __auiInstallValueBridge(el, 'naturalWidth', () => el.getNaturalWidth ? el.getNaturalWidth() : 0);
      __auiInstallValueBridge(el, 'naturalHeight', () => el.getNaturalHeight ? el.getNaturalHeight() : 0);
      __auiInstallValueBridge(el, 'complete', () => el.isComplete ? !!el.isComplete() : false);
      __auiInstallValueBridge(el, 'children', () => __auiDecorateList(el.getChildren()));
      __auiInstallValueBridge(el, 'options', () => __auiDecorateList(el.getOptions()));
      __auiInstallValueBridge(el, 'selectedOptions', () => __auiDecorateList(el.getSelectedOptions()));
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
  __auiDecorateElement(document.body);
} catch (e) {}
