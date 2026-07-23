import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createServer } from "../server.mjs";

test("MCP server initializes and exposes the Apricity tool set", async () => {
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const server = createServer();
  const client = new Client({ name: "apricity-mcp-test", version: "1.0.0" });

  await Promise.all([
    server.connect(serverTransport),
    client.connect(clientTransport),
  ]);

  try {
    const { tools } = await client.listTools();
    assert.deepEqual(
      tools.map((tool) => tool.name).sort(),
      [
        "apricity_click",
        "apricity_documents",
        "apricity_fill",
        "apricity_hover",
        "apricity_inspect",
        "apricity_query",
        "apricity_snapshot",
        "apricity_wait_for",
      ],
    );

    const fill = tools.find((tool) => tool.name === "apricity_fill");
    assert.deepEqual(fill.inputSchema.required.sort(), ["selector", "targetId", "value"]);
    assert.equal(fill.inputSchema.properties.targetId.format, "uuid");
  } finally {
    await client.close();
    await server.close();
  }
});

test("tool failures use MCP error results instead of crashing the server", async () => {
  const previousDiscovery = process.env.APRICITY_DEBUG_DISCOVERY;
  process.env.APRICITY_DEBUG_DISCOVERY = "missing-apricity-debug.json";
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const server = createServer();
  const client = new Client({ name: "apricity-mcp-test", version: "1.0.0" });

  await Promise.all([
    server.connect(serverTransport),
    client.connect(clientTransport),
  ]);

  try {
    const result = await client.callTool({ name: "apricity_documents", arguments: {} });
    assert.equal(result.isError, true);
    assert.match(result.content[0].text, /Apricity debugger error/);
  } finally {
    if (previousDiscovery === undefined) delete process.env.APRICITY_DEBUG_DISCOVERY;
    else process.env.APRICITY_DEBUG_DISCOVERY = previousDiscovery;
    await client.close();
    await server.close();
  }
});
