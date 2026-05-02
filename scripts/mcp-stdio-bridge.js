#!/usr/bin/env node
/**
 * MCP stdio-to-HTTP bridge for PSI Agent.
 *
 * Claude Desktop and other MCP clients connect to this process over stdio.
 * The bridge exposes MCP tools and forwards them to the IntelliJ HTTP server
 * running at PSI_AGENT_URL.
 *
 * Usage:
 *   node scripts/mcp-stdio-bridge.js
 *
 * Environment:
 *   PSI_AGENT_URL  — base URL (default: http://127.0.0.1:9742)
 */

const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const { StdioServerTransport } = require("@modelcontextprotocol/sdk/server/stdio.js");
const { ListToolsRequestSchema, CallToolRequestSchema } = require("@modelcontextprotocol/sdk/types.js");

const BASE_URL = process.env.PSI_AGENT_URL || "http://127.0.0.1:9742";
const TOKEN_FILE = process.env.PSI_AGENT_TOKEN_FILE || path.join(os.homedir(), ".psi-agent", "token");

function loadAuthToken() {
  if (process.env.PSI_AGENT_TOKEN) return process.env.PSI_AGENT_TOKEN.trim();
  try {
    const token = fs.readFileSync(TOKEN_FILE, "utf8").trim();
    return token || null;
  } catch {
    return null;
  }
}

function buildHeaders(payload) {
  const headers = payload
    ? {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(payload),
      }
    : {};

  const token = loadAuthToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

function requestJson(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const payload = body == null ? null : JSON.stringify(body);
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        method,
        headers: buildHeaders(payload),
      },
      (res) => {
        let chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const text = Buffer.concat(chunks).toString();
          if (!text) {
            resolve(null);
            return;
          }
          try {
            const parsed = JSON.parse(text);
            if (res.statusCode >= 400) {
              reject(new Error(parsed?.error || `HTTP ${res.statusCode}`));
              return;
            }
            resolve(parsed);
          } catch {
            reject(new Error(`Invalid JSON from server: ${text.slice(0, 200)}`));
          }
        });
      }
    );
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

function normalizeToolsList(data) {
  if (Array.isArray(data)) return { tools: data };
  if (Array.isArray(data?.tools)) return { tools: data.tools };
  return { tools: [] };
}

function normalizeToolCallResult(data) {
  if (data && typeof data === "object" && data.error) {
    return {
      content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      isError: true,
    };
  }

  return {
    content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
  };
}

async function main() {
  const server = new Server(
    { name: "psi-agent", version: "1.0.0" },
    { capabilities: { tools: {} } }
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => {
    const data = await requestJson("GET", "/mcp/tools/list");
    return normalizeToolsList(data);
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const data = await requestJson("POST", "/mcp/tools/call", {
      name: request.params.name,
      arguments: request.params.arguments || {},
    });
    return normalizeToolCallResult(data);
  });

  const transport = new StdioServerTransport();
  await server.connect(transport);
  process.stderr.write(`PSI Agent MCP stdio bridge started at ${BASE_URL}\n`);
}

main().catch((error) => {
  process.stderr.write(`PSI Agent MCP stdio bridge failed: ${error.message}\n`);
  process.exit(1);
});
