#!/usr/bin/env node
/**
 * MCP stdio-to-HTTP bridge for PSI Agent.
 *
 * Claude Desktop and other MCP clients communicate via stdin/stdout JSON-RPC.
 * This bridge translates those messages to HTTP calls to the PSI Agent server
 * running inside IntelliJ.
 *
 * Usage (standalone):   node scripts/mcp-stdio-bridge.js
 * Usage (Claude Desktop): configure in mcp-config.json (see project root)
 *
 * Environment:
 *   PSI_AGENT_URL  — base URL (default: http://127.0.0.1:9742)
 */

const http = require("http");
const readline = require("readline");

const BASE_URL = process.env.PSI_AGENT_URL || "http://127.0.0.1:9742";

// ── HTTP helper ─────────────────────────────────────────────────────────────

function httpPost(path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const data = JSON.stringify(body);
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString()));
          } catch {
            resolve({ error: "Invalid JSON from server" });
          }
        });
      }
    );
    req.on("error", (e) => reject(e));
    req.write(data);
    req.end();
  });
}

function httpGet(path) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    http
      .get(url, (res) => {
        let chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString()));
          } catch {
            resolve({ error: "Invalid JSON from server" });
          }
        });
      })
      .on("error", (e) => reject(e));
  });
}

// ── JSON-RPC helpers ────────────────────────────────────────────────────────

function sendResponse(id, result) {
  const msg = { jsonrpc: "2.0", id, result };
  const json = JSON.stringify(msg);
  process.stdout.write(`Content-Length: ${Buffer.byteLength(json)}\r\n\r\n${json}`);
}

function sendError(id, code, message) {
  const msg = { jsonrpc: "2.0", id, error: { code, message } };
  const json = JSON.stringify(msg);
  process.stdout.write(`Content-Length: ${Buffer.byteLength(json)}\r\n\r\n${json}`);
}

// ── MCP message handling ────────────────────────────────────────────────────

async function handleMessage(msg) {
  const { id, method, params } = msg;

  try {
    switch (method) {
      case "initialize":
        sendResponse(id, {
          protocolVersion: "2024-11-05",
          capabilities: { tools: {} },
          serverInfo: { name: "psi-agent", version: "1.0.0" },
        });
        break;

      case "tools/list": {
        const data = await httpGet("/mcp/tools/list");
        sendResponse(id, data);
        break;
      }

      case "tools/call": {
        const toolName = params?.name;
        const args = params?.arguments || {};
        const data = await httpPost("/mcp/tools/call", {
          name: toolName,
          arguments: args,
        });
        sendResponse(id, {
          content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
        });
        break;
      }

      case "notifications/initialized":
        // No response needed for notifications
        break;

      default:
        sendError(id, -32601, `Method not found: ${method}`);
    }
  } catch (e) {
    sendError(id, -32000, `Server error: ${e.message}`);
  }
}

// ── stdin reader (Content-Length framed JSON-RPC) ───────────────────────────

let buffer = "";

process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  buffer += chunk;

  while (true) {
    const headerEnd = buffer.indexOf("\r\n\r\n");
    if (headerEnd === -1) break;

    const header = buffer.substring(0, headerEnd);
    const match = header.match(/Content-Length:\s*(\d+)/i);
    if (!match) {
      // Try line-delimited JSON as fallback
      const lineEnd = buffer.indexOf("\n");
      if (lineEnd !== -1) {
        const line = buffer.substring(0, lineEnd).trim();
        buffer = buffer.substring(lineEnd + 1);
        if (line) {
          try {
            handleMessage(JSON.parse(line));
          } catch {}
        }
      }
      break;
    }

    const contentLength = parseInt(match[1], 10);
    const bodyStart = headerEnd + 4;
    if (buffer.length < bodyStart + contentLength) break;

    const body = buffer.substring(bodyStart, bodyStart + contentLength);
    buffer = buffer.substring(bodyStart + contentLength);

    try {
      handleMessage(JSON.parse(body));
    } catch (e) {
      process.stderr.write(`Parse error: ${e.message}\n`);
    }
  }
});

process.stderr.write("PSI Agent MCP stdio bridge started\n");

