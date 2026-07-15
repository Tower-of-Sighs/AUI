const { spawn } = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const net = require("net");
const os = require("os");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..");
const harnessPath = path.join(
  repoRoot,
  "src",
  "main",
  "resources",
  "assets",
  "apricityui",
  "apricity",
  "tests",
  "resource-browser-browser-metrics.html"
);

const chromeCandidates = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
].filter(Boolean);

const chrome = chromeCandidates.find((candidate) => fs.existsSync(candidate));
if (!chrome) {
  console.error("No Chrome/Edge executable found. Set CHROME_PATH to a Chromium-compatible browser.");
  process.exit(1);
}

const hoverTargetArg = process.argv.find((arg) => arg.startsWith("--target="));
const hoverTarget = hoverTargetArg ? hoverTargetArg.slice("--target=".length) : "header-button-0";
const targetSpec = {
  "header-button-0": {
    selector: ".action-btn",
    label: "header-button-0",
    pseudoBefore: true,
  },
  "file-card-0": {
    selector: ".file-card",
    label: "file-card-0",
    pseudoBefore: true,
    pseudoAfter: true,
    children: {
      fileIcon: ".file-icon",
      fileName: ".file-name",
    },
  },
  "tree-item-0": {
    selector: ".tree-item",
    label: "tree-item-0",
    children: {
      treeToggle: ".tree-toggle",
      treeIcon: ".tree-icon",
      treeLabel: "span",
    },
  },
}[hoverTarget];

if (!targetSpec) {
  console.error(`Unsupported hover target: ${hoverTarget}`);
  process.exit(2);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function httpJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = "";
      res.setEncoding("utf8");
      res.on("data", (chunk) => data += chunk);
      res.on("end", () => {
        try {
          resolve(JSON.parse(data));
        } catch (error) {
          reject(error);
        }
      });
    }).on("error", reject);
  });
}

async function waitForJson(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      return await httpJson(url);
    } catch (error) {
      lastError = error;
      await delay(100);
    }
  }
  throw lastError || new Error(`Timed out waiting for ${url}`);
}

function websocketConnect(wsUrl) {
  const parsed = new URL(wsUrl);
  const socket = net.connect(Number(parsed.port), parsed.hostname);
  const key = crypto.randomBytes(16).toString("base64");
  let handshake = "";
  let connected = false;
  let buffer = Buffer.alloc(0);
  let nextId = 1;
  const pending = new Map();
  let readyResolve;
  let readyReject;
  const ready = new Promise((resolve, reject) => {
    readyResolve = resolve;
    readyReject = reject;
  });

  function sendFrame(payload) {
    const body = Buffer.from(payload, "utf8");
    const mask = crypto.randomBytes(4);
    let header;
    if (body.length < 126) {
      header = Buffer.alloc(2);
      header[1] = 0x80 | body.length;
    } else if (body.length < 65536) {
      header = Buffer.alloc(4);
      header[1] = 0x80 | 126;
      header.writeUInt16BE(body.length, 2);
    } else {
      header = Buffer.alloc(10);
      header[1] = 0x80 | 127;
      header.writeBigUInt64BE(BigInt(body.length), 2);
    }
    header[0] = 0x81;
    const masked = Buffer.alloc(body.length);
    for (let i = 0; i < body.length; i++) masked[i] = body[i] ^ mask[i % 4];
    socket.write(Buffer.concat([header, mask, masked]));
  }

  function readFrame() {
    if (buffer.length < 2) return null;
    const first = buffer[0];
    const second = buffer[1];
    const opcode = first & 0x0f;
    const masked = (second & 0x80) !== 0;
    let length = second & 0x7f;
    let offset = 2;
    if (length === 126) {
      if (buffer.length < offset + 2) return null;
      length = buffer.readUInt16BE(offset);
      offset += 2;
    } else if (length === 127) {
      if (buffer.length < offset + 8) return null;
      length = Number(buffer.readBigUInt64BE(offset));
      offset += 8;
    }
    let mask;
    if (masked) {
      if (buffer.length < offset + 4) return null;
      mask = buffer.subarray(offset, offset + 4);
      offset += 4;
    }
    if (buffer.length < offset + length) return null;
    let payload = buffer.subarray(offset, offset + length);
    buffer = buffer.subarray(offset + length);
    if (masked) {
      const unmasked = Buffer.alloc(payload.length);
      for (let i = 0; i < payload.length; i++) unmasked[i] = payload[i] ^ mask[i % 4];
      payload = unmasked;
    }
    if (opcode === 0x8) socket.end();
    if (opcode !== 0x1) return "";
    return payload.toString("utf8");
  }

  socket.on("data", (chunk) => {
    if (!connected) {
      handshake += chunk.toString("binary");
      const marker = handshake.indexOf("\r\n\r\n");
      if (marker < 0) return;
      if (!/^HTTP\/1\.1 101\b/.test(handshake)) {
        readyReject(new Error(`WebSocket handshake failed: ${handshake.slice(0, marker)}`));
        socket.end();
        return;
      }
      connected = true;
      readyResolve();
      const rest = Buffer.from(handshake.slice(marker + 4), "binary");
      buffer = Buffer.concat([buffer, rest]);
    } else {
      buffer = Buffer.concat([buffer, chunk]);
    }

    let message;
    while ((message = readFrame()) !== null) {
      if (!message) continue;
      const payload = JSON.parse(message);
      if (payload.id && pending.has(payload.id)) {
        const { resolve, reject } = pending.get(payload.id);
        pending.delete(payload.id);
        if (payload.error) reject(new Error(JSON.stringify(payload.error)));
        else resolve(payload.result);
      }
    }
  });
  socket.on("error", (error) => {
    readyReject(error);
    for (const entry of pending.values()) entry.reject(error);
    pending.clear();
  });

  socket.write(
    `GET ${parsed.pathname}${parsed.search} HTTP/1.1\r\n` +
    `Host: ${parsed.host}\r\n` +
    "Upgrade: websocket\r\n" +
    "Connection: Upgrade\r\n" +
    `Sec-WebSocket-Key: ${key}\r\n` +
    "Sec-WebSocket-Version: 13\r\n\r\n"
  );

  return {
    async call(method, params = {}) {
      await ready;
      const id = nextId++;
      const payload = JSON.stringify({ id, method, params });
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        sendFrame(payload);
      });
    },
    close() {
      socket.end();
    },
  };
}

async function main() {
  const port = 9333 + Math.floor(Math.random() * 1000);
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "aui-hover-chrome-"));
  const screenshotDir = path.join(repoRoot, "run", "screenshots", "browser");
  fs.mkdirSync(screenshotDir, { recursive: true });

  const params = new URLSearchParams({ static: "1" });
  const fileUrl = `file:///${harnessPath.replace(/\\/g, "/")}?${params.toString()}`;
  const chromeProcess = spawn(chrome, [
    "--headless=new",
    "--disable-gpu",
    "--disable-background-networking",
    "--allow-file-access-from-files",
    "--window-size=1487,942",
    "--force-device-scale-factor=1",
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    fileUrl,
  ], { stdio: "ignore" });

  try {
    const tabs = await waitForJson(`http://127.0.0.1:${port}/json/list`, 10000);
    const page = tabs.find((tab) => tab.type === "page");
    if (!page || !page.webSocketDebuggerUrl) throw new Error("Could not find page websocket.");
    const cdp = websocketConnect(page.webSocketDebuggerUrl);

    await cdp.call("Runtime.enable");
    await cdp.call("Page.enable");
    await cdp.call("DOM.enable");
    await cdp.call("CSS.enable");
    await delay(1800);
    await cdp.call("DOM.getDocument", { depth: -1, pierce: true });

    const nodeEval = await cdp.call("Runtime.evaluate", {
      returnByValue: false,
      expression: `(() => {
        const frame = document.getElementById('target');
        const doc = frame.contentDocument;
        return doc.querySelector(${JSON.stringify(targetSpec.selector)});
      })()`,
    });
    if (!nodeEval.result || !nodeEval.result.objectId) {
      throw new Error(`Hover target not found for selector ${targetSpec.selector}`);
    }
    let node = await cdp.call("DOM.requestNode", { objectId: nodeEval.result.objectId });
    if (!node.nodeId) {
      const described = await cdp.call("DOM.describeNode", { objectId: nodeEval.result.objectId });
      if (!described.node || !described.node.backendNodeId) {
        throw new Error(`Could not resolve hover target node for selector ${targetSpec.selector}`);
      }
      const pushed = await cdp.call("DOM.pushNodesByBackendIdsToFrontend", {
        backendNodeIds: [described.node.backendNodeId],
      });
      node = { nodeId: pushed.nodeIds && pushed.nodeIds[0] };
    }
    if (!node.nodeId) {
      throw new Error(`Could not map hover target to a frontend node for selector ${targetSpec.selector}`);
    }
    await cdp.call("CSS.forcePseudoState", {
      nodeId: node.nodeId,
      forcedPseudoClasses: ["hover"],
    });
    await delay(350);

    const metricsEval = await cdp.call("Runtime.evaluate", {
      returnByValue: true,
      expression: `(() => {
        const frame = document.getElementById('target');
        const doc = frame.contentDocument;
        const win = frame.contentWindow;
        const node = doc.querySelector(${JSON.stringify(targetSpec.selector)});
        const rect = node.getBoundingClientRect();
        const style = win.getComputedStyle(node);
        const before = win.getComputedStyle(node, '::before');
        const after = win.getComputedStyle(node, '::after');
        const active = doc.querySelector(':hover');
        function childPayload(name, selector) {
          const child = node.querySelector(selector);
          if (!child) return null;
          const childRect = child.getBoundingClientRect();
          return {
            name,
            selector,
            text: (child.textContent || '').trim(),
            rect: { x: childRect.x, y: childRect.y, width: childRect.width, height: childRect.height, right: childRect.right, bottom: childRect.bottom },
            style: pick(win.getComputedStyle(child))
          };
        }
        function pick(s) {
          return {
            backgroundColor: s.backgroundColor,
            color: s.color,
            borderTopColor: s.borderTopColor,
            borderRightColor: s.borderRightColor,
            borderBottomColor: s.borderBottomColor,
            borderLeftColor: s.borderLeftColor,
            borderTopWidth: s.borderTopWidth,
            borderRightWidth: s.borderRightWidth,
            borderBottomWidth: s.borderBottomWidth,
            borderLeftWidth: s.borderLeftWidth,
            boxShadow: s.boxShadow,
            transform: s.transform,
            transition: s.transition,
            position: s.position,
            left: s.left,
            top: s.top,
            width: s.width,
            height: s.height,
            content: s.content,
            clipPath: s.clipPath
          };
        }
        return {
          target: ${JSON.stringify(targetSpec.label)},
          viewport: {
            innerWidth: win.innerWidth,
            innerHeight: win.innerHeight,
            clientWidth: doc.documentElement.clientWidth,
            clientHeight: doc.documentElement.clientHeight
          },
          forcedPseudoClasses: ['hover'],
          hoverChain: Array.from(doc.querySelectorAll(':hover')).map(n => ({
            tag: n.tagName,
            className: n.className || '',
            text: (n.textContent || '').trim()
          })),
          targetRect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom },
          targetStyle: pick(style),
          beforeStyle: pick(before),
          afterStyle: pick(after),
          childStyles: Object.entries(${JSON.stringify(targetSpec.children || {})}).map(([name, selector]) => childPayload(name, selector))
        };
      })()`,
    });
    const payload = metricsEval.result.value;

    const screenshotPath = path.join(screenshotDir, `resource-browser-hover-${hoverTarget}-1463x843.png`);
    const shot = await cdp.call("Page.captureScreenshot", { format: "png", fromSurface: true });
    fs.writeFileSync(screenshotPath, Buffer.from(shot.data, "base64"));
    payload.screenshot = path.relative(repoRoot, screenshotPath).replace(/\\/g, "/");

    console.log("BROWSER_RESOURCE_HOVER_METRICS " + JSON.stringify(payload));
    cdp.close();
  } finally {
    chromeProcess.kill();
    await delay(250);
    try {
      fs.rmSync(userDataDir, { recursive: true, force: true });
    } catch (error) {
      // Chrome Crashpad can keep a metrics file briefly locked on Windows.
    }
  }
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
