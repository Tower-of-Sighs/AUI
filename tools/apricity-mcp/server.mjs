#!/usr/bin/env node

import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import WebSocket from "ws";
import { z } from "zod";
import { connect } from "../apricity-debug-client.mjs";

if (typeof globalThis.WebSocket === "undefined") globalThis.WebSocket = WebSocket;

const MCP_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_DISCOVERY_FILE = path.resolve(MCP_DIR, "../../run/apricity/debug.json");
const TARGET_ID = z.string().uuid().describe("Full UUID from apricity_documents");
const SELECTOR = z.string().min(1).describe("CSS selector evaluated in the target document");

export function createServer() {
  const server = new McpServer({
    name: "apricity-debug",
    version: "1.0.0",
  });

  server.registerTool(
    "apricity_documents",
    {
      title: "List Apricity documents",
      description: "List all live Apricity UI documents. Use the full targetId for subsequent tools.",
      inputSchema: {},
    },
    tool(async () => withClient(async (client) => ({ documents: await client.documents() }))),
  );

  server.registerTool(
    "apricity_snapshot",
    {
      title: "Snapshot Apricity DOM",
      description: "Return a bounded DOM snapshot for one live Apricity document.",
      inputSchema: {
        targetId: TARGET_ID,
        maxDepth: z.number().int().min(0).max(128).default(32),
        maxNodes: z.number().int().min(1).max(20000).default(5000),
      },
    },
    tool(({ targetId, maxDepth, maxNodes }) => withPage(
      targetId,
      (page) => page.snapshot({ maxDepth, maxNodes }),
    )),
  );

  server.registerTool(
    "apricity_query",
    {
      title: "Query Apricity elements",
      description: "Query elements and return bounded attribute, text, and box summaries.",
      inputSchema: {
        targetId: TARGET_ID,
        selector: SELECTOR,
        maxResults: z.number().int().min(1).max(100).default(20),
      },
    },
    tool(({ targetId, selector, maxResults }) => withPage(
      targetId,
      (page) => queryElements(page, selector, maxResults),
    )),
  );

  server.registerTool(
    "apricity_inspect",
    {
      title: "Inspect Apricity element",
      description: "Inspect the first matching element, including attributes, text, box model, and optional computed CSS.",
      inputSchema: {
        targetId: TARGET_ID,
        selector: SELECTOR,
        includeComputedStyle: z.boolean().default(false),
      },
    },
    tool(({ targetId, selector, includeComputedStyle }) => withPage(
      targetId,
      async (page) => {
        const { nodeIds } = await page.call("DOM.queryAll", { selector });
        if (nodeIds.length === 0) throw new Error(`No element matches ${selector}`);
        const nodeId = nodeIds[0];
        const [attributesResult, textResult, boxModel, computedStyle] = await Promise.all([
          page.call("DOM.getAttributes", { nodeId }),
          page.call("DOM.getText", { nodeId }),
          page.call("DOM.getBoxModel", { nodeId }),
          includeComputedStyle ? page.call("DOM.getComputedStyle", { nodeId }) : null,
        ]);
        const result = {
          selector,
          count: nodeIds.length,
          attributes: attributesResult.attributes,
          text: truncate(textResult.text),
          boxModel,
        };
        if (includeComputedStyle) result.computedStyle = computedStyle;
        return result;
      },
    )),
  );

  server.registerTool(
    "apricity_wait_for",
    {
      title: "Wait for Apricity element",
      description: "Wait until an element reaches an attached, detached, visible, or hidden state.",
      inputSchema: {
        targetId: TARGET_ID,
        selector: SELECTOR,
        state: z.enum(["attached", "detached", "visible", "hidden"]).default("visible"),
        timeout: z.number().int().min(0).max(60000).default(5000),
      },
    },
    tool(({ targetId, selector, state, timeout }) => withPage(
      targetId,
      async (page) => {
        await page.locator(selector).waitFor({ state, timeout });
        return { selector, state, matched: true };
      },
    )),
  );

  server.registerTool(
    "apricity_hover",
    {
      title: "Hover Apricity element",
      description: "Move the Apricity pointer to the center of the first matching element.",
      inputSchema: { targetId: TARGET_ID, selector: SELECTOR },
    },
    tool(({ targetId, selector }) => withPage(
      targetId,
      async (page) => ({ selector, ...(await page.locator(selector).hover()) }),
    )),
  );

  server.registerTool(
    "apricity_click",
    {
      title: "Click Apricity element",
      description: "Send trusted mousemove, mousedown, and mouseup events to the first matching element.",
      inputSchema: { targetId: TARGET_ID, selector: SELECTOR },
    },
    tool(({ targetId, selector }) => withPage(
      targetId,
      async (page) => ({ selector, ...(await page.locator(selector).click()) }),
    )),
  );

  server.registerTool(
    "apricity_fill",
    {
      title: "Fill Apricity text control",
      description: "Focus the first matching input or textarea and replace its value.",
      inputSchema: {
        targetId: TARGET_ID,
        selector: SELECTOR,
        value: z.string(),
      },
    },
    tool(({ targetId, selector, value }) => withPage(
      targetId,
      async (page) => ({ selector, ...(await page.locator(selector).fill(value)) }),
    )),
  );

  return server;
}

export async function main() {
  const server = createServer();
  await server.connect(new StdioServerTransport());
}

async function queryElements(page, selector, maxResults) {
  const { nodeIds } = await page.call("DOM.queryAll", { selector });
  const results = await Promise.all(nodeIds.slice(0, maxResults).map(async (nodeId) => {
    const [attributesResult, textResult, boxModel] = await Promise.all([
      page.call("DOM.getAttributes", { nodeId }),
      page.call("DOM.getText", { nodeId }),
      page.call("DOM.getBoxModel", { nodeId }),
    ]);
    return {
      attributes: attributesResult.attributes,
      text: truncate(textResult.text),
      boxModel,
    };
  }));
  return {
    selector,
    count: nodeIds.length,
    truncated: nodeIds.length > results.length,
    elements: results,
  };
}

async function withClient(action) {
  const client = await connect(debugConnectionOptions());
  try {
    return await action(client);
  } finally {
    client.close();
  }
}

async function withPage(targetId, action) {
  return withClient(async (client) => {
    const page = await client.attach(targetId);
    try {
      return await action(page);
    } finally {
      await page.detach().catch(() => {});
    }
  });
}

function debugConnectionOptions() {
  const endpoint = process.env.APRICITY_DEBUG_ENDPOINT;
  if (endpoint) {
    const token = process.env.APRICITY_DEBUG_TOKEN;
    if (!token) throw new Error("APRICITY_DEBUG_TOKEN is required with APRICITY_DEBUG_ENDPOINT");
    return { endpoint, token };
  }
  return {
    discoveryFile: path.resolve(process.env.APRICITY_DEBUG_DISCOVERY ?? DEFAULT_DISCOVERY_FILE),
  };
}

function tool(handler) {
  return async (input) => {
    try {
      return success(await handler(input));
    } catch (error) {
      return failure(error);
    }
  };
}

function success(data) {
  const structured = data && typeof data === "object" ? data : { value: data };
  return {
    content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
    structuredContent: structured,
  };
}

function failure(error) {
  const code = error && typeof error === "object" && "code" in error ? ` (${error.code})` : "";
  const message = error instanceof Error ? error.message : String(error);
  return {
    isError: true,
    content: [{ type: "text", text: `Apricity debugger error${code}: ${message}` }],
  };
}

function truncate(value, maxLength = 2000) {
  if (value == null) return "";
  const text = String(value);
  return text.length <= maxLength ? text : `${text.slice(0, maxLength)}...`;
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
