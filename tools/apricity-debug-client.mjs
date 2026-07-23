import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_DISCOVERY_FILE = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../run/apricity/debug.json",
);

export async function connect(options = {}) {
  if (typeof WebSocket === "undefined") {
    throw new Error("The Apricity debug client requires Node.js 22 or a global WebSocket implementation");
  }
  const discoveryFile = options.discoveryFile ?? DEFAULT_DISCOVERY_FILE;
  const discovery = options.endpoint
    ? { endpoint: options.endpoint, token: options.token }
    : JSON.parse(await readFile(discoveryFile, "utf8"));
  if (!discovery.endpoint || !discovery.token) {
    throw new Error(`Invalid Apricity debugger discovery file: ${discoveryFile}`);
  }

  const endpoint = new URL(discovery.endpoint);
  endpoint.searchParams.set("token", discovery.token);
  const socket = new WebSocket(endpoint);
  await waitForSocketOpen(socket, options.timeout ?? 5000);
  return new ApricityDebugClient(socket);
}

export class ApricityDebugClient {
  #socket;
  #nextId = 1;
  #pending = new Map();

  constructor(socket) {
    this.#socket = socket;
    socket.addEventListener("message", (event) => this.#receive(event.data));
    socket.addEventListener("close", () => this.#rejectPending(new Error("Apricity debugger disconnected")));
    socket.addEventListener("error", () => this.#rejectPending(new Error("Apricity debugger connection failed")));
  }

  async info() {
    return this.call("System.info");
  }

  async documents() {
    return (await this.call("Target.list")).targets;
  }

  async attach(targetId) {
    const attached = await this.call("Target.attach", { targetId });
    return new Page(this, attached.sessionId, attached.targetId, attached.path);
  }

  call(method, params = {}) {
    if (this.#socket.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error("Apricity debugger is not connected"));
    }
    const id = this.#nextId++;
    const promise = new Promise((resolve, reject) => this.#pending.set(id, { resolve, reject }));
    this.#socket.send(JSON.stringify({ jsonrpc: "2.0", id, method, params }));
    return promise;
  }

  close() {
    this.#socket.close();
  }

  #receive(raw) {
    let response;
    try {
      response = JSON.parse(typeof raw === "string" ? raw : raw.toString());
    } catch {
      return;
    }
    const pending = this.#pending.get(response.id);
    if (!pending) return;
    this.#pending.delete(response.id);
    if (response.error) {
      const error = new Error(response.error.message);
      error.code = response.error.code;
      pending.reject(error);
    } else {
      pending.resolve(response.result);
    }
  }

  #rejectPending(error) {
    for (const pending of this.#pending.values()) pending.reject(error);
    this.#pending.clear();
  }
}

export class Page {
  constructor(client, sessionId, targetId, documentPath) {
    this.client = client;
    this.sessionId = sessionId;
    this.targetId = targetId;
    this.path = documentPath;
  }

  locator(selector) {
    return new Locator(this, selector);
  }

  async snapshot(options = {}) {
    return this.client.call("DOM.snapshot", {
      sessionId: this.sessionId,
      ...options,
    });
  }

  async detach() {
    return this.client.call("Target.detach", { sessionId: this.sessionId });
  }

  call(method, params = {}) {
    return this.client.call(method, { sessionId: this.sessionId, ...params });
  }
}

export class Locator {
  constructor(page, selector) {
    this.page = page;
    this.selector = selector;
  }

  async count() {
    return (await this.page.call("DOM.queryAll", { selector: this.selector })).nodeIds.length;
  }

  async attributes() {
    return (await this.#nodeCall("DOM.getAttributes")).attributes;
  }

  async text() {
    return (await this.#nodeCall("DOM.getText")).text;
  }

  async computedStyle() {
    return this.#nodeCall("DOM.getComputedStyle");
  }

  async boxModel() {
    return this.#nodeCall("DOM.getBoxModel");
  }

  async hover() {
    return this.#nodeCall("DOM.hover");
  }

  async click() {
    return this.#nodeCall("DOM.click");
  }

  async fill(value) {
    const nodeId = await this.#resolve();
    return this.page.call("DOM.fill", { nodeId, value: String(value) });
  }

  async waitFor(options = {}) {
    const state = options.state ?? "visible";
    const timeout = options.timeout ?? 5000;
    const pollInterval = options.pollInterval ?? 50;
    const deadline = Date.now() + timeout;
    let lastError;
    while (Date.now() <= deadline) {
      try {
        const nodeId = await this.#resolve(false);
        const attached = nodeId !== null;
        let visible = false;
        if (attached && (state === "visible" || state === "hidden")) {
          try {
            const box = await this.page.call("DOM.getBoxModel", { nodeId });
            visible = box.border.width > 0 && box.border.height > 0;
          } catch (error) {
            lastError = error;
          }
        }
        if (state === "attached" && attached) return;
        if (state === "detached" && !attached) return;
        if (state === "visible" && attached && visible) return;
        if (state === "hidden" && (!attached || !visible)) return;
      } catch (error) {
        lastError = error;
      }
      await delay(pollInterval);
    }
    const detail = lastError ? `: ${lastError.message}` : "";
    throw new Error(`Timed out waiting for ${this.selector} to be ${state}${detail}`);
  }

  async #nodeCall(method) {
    const nodeId = await this.#resolve();
    return this.page.call(method, { nodeId });
  }

  async #resolve(required = true) {
    const { nodeId } = await this.page.call("DOM.query", { selector: this.selector });
    if (required && nodeId === null) throw new Error(`No element matches ${this.selector}`);
    return nodeId;
  }
}

function waitForSocketOpen(socket, timeout) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      socket.close();
      reject(new Error("Timed out connecting to Apricity debugger"));
    }, timeout);
    const onOpen = () => {
      cleanup();
      resolve();
    };
    const onError = () => {
      cleanup();
      reject(new Error("Unable to connect to Apricity debugger"));
    };
    const cleanup = () => {
      clearTimeout(timer);
      socket.removeEventListener("open", onOpen);
      socket.removeEventListener("error", onError);
    };
    socket.addEventListener("open", onOpen);
    socket.addEventListener("error", onError);
  });
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
