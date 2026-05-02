#!/usr/bin/env node
const http = require('http');
const { spawn } = require('child_process');
const path = require('path');

function startMockBackend() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let body = '';
      req.on('data', (chunk) => (body += chunk));
      req.on('end', () => {
        if (req.method === 'GET' && req.url === '/mcp/tools/list') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(
            JSON.stringify({
              tools: [
                {
                  name: 'psi_search',
                  description: 'Search for code elements',
                  annotations: { readOnlyHint: true },
                  inputSchema: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'] },
                },
              ],
            })
          );
          return;
        }

        if (req.method === 'POST' && req.url === '/mcp/tools/call') {
          const parsed = body ? JSON.parse(body) : {};
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ ok: true, tool: parsed.name, arguments: parsed.arguments || {} }));
          return;
        }

        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'not found' }));
      });
    });

    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function readLines(stream, onMessage) {
  let buffer = '';
  stream.setEncoding('utf8');
  stream.on('data', (chunk) => {
    buffer += chunk;
    while (true) {
      const lineEnd = buffer.indexOf('\n');
      if (lineEnd === -1) break;
      const line = buffer.slice(0, lineEnd).replace(/\r$/, '');
      buffer = buffer.slice(lineEnd + 1);
      if (line.trim()) {
        onMessage(JSON.parse(line));
      }
    }
  });
}

function sendMessage(proc, message) {
  proc.stdin.write(`${JSON.stringify(message)}\n`);
}

(async () => {
  const backend = await startMockBackend();
  const port = backend.address().port;
  const bridgePath = path.join(__dirname, 'mcp-stdio-bridge.js');
  const bridge = spawn(process.execPath, [bridgePath], {
    env: {
      ...process.env,
      PSI_AGENT_URL: `http://127.0.0.1:${port}`,
    },
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  const messages = [];
  readLines(bridge.stdout, (msg) => messages.push(msg));

  let stderr = '';
  bridge.stderr.setEncoding('utf8');
  bridge.stderr.on('data', (chunk) => (stderr += chunk));

  sendMessage(bridge, {
    jsonrpc: '2.0',
    id: 1,
    method: 'initialize',
    params: {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'bridge-smoke-test', version: '1.0.0' },
    },
  });

  const initResponse = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timed out waiting for initialize response\n${stderr}`)), 10000);

    const interval = setInterval(() => {
      const init = messages.find((m) => m.id === 1);
      if (init) {
        clearTimeout(timeout);
        clearInterval(interval);
        resolve(init);
      }
    }, 50);

    bridge.on('exit', (code) => {
      clearTimeout(timeout);
      clearInterval(interval);
      reject(new Error(`Bridge exited with code ${code}\n${stderr}`));
    });
  });

  if (!initResponse.result || initResponse.result.serverInfo?.name !== 'psi-agent') {
    throw new Error(`Bad initialize response: ${JSON.stringify(initResponse, null, 2)}`);
  }

  sendMessage(bridge, { jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });

  const toolsResponse = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timed out waiting for tools/list response\n${stderr}`)), 10000);

    const interval = setInterval(() => {
      const tools = messages.find((m) => m.id === 2);
      if (tools) {
        clearTimeout(timeout);
        clearInterval(interval);
        resolve(tools);
      }
    }, 50);

    bridge.on('exit', (code) => {
      clearTimeout(timeout);
      clearInterval(interval);
      reject(new Error(`Bridge exited with code ${code}\n${stderr}`));
    });
  });

  if (!toolsResponse.result || !Array.isArray(toolsResponse.result.tools) || toolsResponse.result.tools.length === 0) {
    throw new Error(`Bad tools/list response: ${JSON.stringify(toolsResponse, null, 2)}`);
  }

  bridge.stdin.end();
  bridge.kill();
  backend.close();

  console.log('MCP bridge smoke test passed');
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
