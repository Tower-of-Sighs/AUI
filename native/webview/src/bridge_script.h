// Injected before page scripts; only the top-level document gets the convenience API.
#pragma once
inline constexpr const wchar_t* kBridgeScript = LR"AUIJS(
(() => {
    if (window !== window.top || window.aui || !window.chrome?.webview) return;
    const transport = window.chrome.webview;
    const session = crypto.randomUUID();
    const pending = new Map(), listeners = new Map();
    let sequence = 0, connected = false, closed = false;
    const error = (code, message) => Object.assign(new Error(message), {code});
    let resolveReady, rejectReady;
    const ready = new Promise((resolve, reject) => { resolveReady = resolve; rejectReady = reject; });
    ready.catch(() => {});
    const send = value => {
        const packet = {v: 1, session, ...value};
        if (new TextEncoder().encode(JSON.stringify(packet)).length > 65536)
            throw error('MESSAGE_TOO_LARGE', 'Message exceeds 64 KiB');
        transport.postMessage(packet);
    };
    const failPending = (code, message) => {
        for (const item of pending.values()) { clearTimeout(item.timer); item.reject(error(code, message)); }
        pending.clear();
    };
    transport.addEventListener('message', event => {
        const m = event.data;
        if (!m || m.v !== 1) return;
        if (m.type === 'overflow') { failPending('QUEUE_FULL', 'Native message queue is full'); return; }
        if (m.session !== session) return;
        if (m.type === 'ready' && !closed) { connected = true; clearInterval(helloTimer); resolveReady(); }
        if (m.type === 'close') {
            closed = true; connected = false; clearInterval(helloTimer);
            rejectReady(error('BRIDGE_CLOSED', 'Bridge closed'));
            failPending('BRIDGE_CLOSED', 'Bridge closed');
        }
        if (m.type === 'response') {
            const item = pending.get(m.id);
            if (!item) return;
            pending.delete(m.id); clearTimeout(item.timer);
            if (m.ok) item.resolve(m.result);
            else item.reject(error(m.error?.code || 'JAVA_ERROR', m.error?.message || 'Request failed'));
        }
        if (m.type === 'event') {
            for (const listener of [...(listeners.get(m.event) || [])]) {
                try { listener(m.data); } catch (e) { console.error(e); }
            }
        }
    });
    const hello = () => { if (!connected && !closed) send({type: 'hello'}); };
    const helloTimer = setInterval(hello, 250);
    const readyTimer = setTimeout(() => {
        if (!connected) { closed = true; clearInterval(helloTimer); rejectReady(error('NOT_AUTHORIZED', 'Bridge not enabled or source not trusted')); }
    }, 10000);
    Object.defineProperty(window, 'aui', {value: Object.freeze({
        ready: () => ready,
        async call(method, params = {}, timeoutMs = 10000) {
            await ready;
            if (closed) throw error('BRIDGE_CLOSED', 'Bridge closed');
            if (pending.size >= 64) throw error('QUEUE_FULL', 'Too many pending calls');
            if (typeof method !== 'string' || !method || method.length > 128)
                throw error('INVALID_REQUEST', 'Invalid method');
            if (!Number.isFinite(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000)
                throw error('INVALID_REQUEST', 'Timeout must be between 1 and 60000 ms');
            const id = String(++sequence);
            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => { pending.delete(id); reject(error('TIMEOUT', 'Request timed out')); }, timeoutMs);
                pending.set(id, {resolve, reject, timer});
                try { send({type: 'request', id, method, params}); }
                catch (e) { pending.delete(id); clearTimeout(timer); reject(e); }
            });
        },
        on(name, listener) {
            if (typeof listener !== 'function') throw new TypeError('Listener must be a function');
            if (!listeners.has(name)) listeners.set(name, new Set());
            const set = listeners.get(name); set.add(listener);
            return () => { set.delete(listener); if (!set.size) listeners.delete(name); };
        }
    }), configurable: false, writable: false});
    window.addEventListener('pagehide', () => {
        closed = true; clearInterval(helloTimer); clearTimeout(readyTimer);
        rejectReady(error('BRIDGE_CLOSED', 'Page unloaded')); failPending('BRIDGE_CLOSED', 'Page unloaded');
        listeners.clear();
    });
    hello();
})();
)AUIJS";
