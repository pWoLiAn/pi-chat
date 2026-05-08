const { spawn } = require("child_process");
const { StringDecoder } = require("string_decoder");
const { WebSocketServer } = require("ws");

const PORT = 8080;
const wss = new WebSocketServer({ port: PORT });

console.log(`Pi WebSocket bridge listening on ws://0.0.0.0:${PORT}`);

wss.on("connection", (ws, req) => {
  const clientAddr = req.socket.remoteAddress;
  console.log(`[+] Client connected: ${clientAddr}`);

  // Spawn pi in RPC mode for this connection
  const pi = spawn("pi", ["--mode", "rpc", "--no-session"], {
    stdio: ["pipe", "pipe", "pipe"],
    env: { ...process.env },
  });

  console.log(`[+] Pi process spawned (pid: ${pi.pid})`);

  // Stream pi stdout → WebSocket (JSONL)
  attachJsonlReader(pi.stdout, (line) => {
    if (ws.readyState === ws.OPEN) {
      ws.send(line);
    }
  });

  // Log pi stderr
  pi.stderr.on("data", (chunk) => {
    console.error(`[pi stderr] ${chunk.toString().trim()}`);
  });

  pi.on("exit", (code) => {
    console.log(`[-] Pi process exited (code: ${code})`);
    if (ws.readyState === ws.OPEN) {
      ws.close(1000, "Pi process exited");
    }
  });

  // WebSocket → pi stdin (forward commands)
  ws.on("message", (data) => {
    const msg = data.toString();
    console.log(`[>] ${msg.substring(0, 120)}`);

    if (!pi.stdin.destroyed) {
      pi.stdin.write(msg + "\n");
    }
  });

  ws.on("close", () => {
    console.log(`[-] Client disconnected: ${clientAddr}`);
    pi.kill("SIGTERM");
  });

  ws.on("error", (err) => {
    console.error(`[!] WebSocket error: ${err.message}`);
    pi.kill("SIGTERM");
  });
});

// JSONL reader that splits on \n only (per pi RPC spec)
function attachJsonlReader(stream, onLine) {
  const decoder = new StringDecoder("utf8");
  let buffer = "";

  stream.on("data", (chunk) => {
    buffer += typeof chunk === "string" ? chunk : decoder.write(chunk);

    while (true) {
      const idx = buffer.indexOf("\n");
      if (idx === -1) break;

      let line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 1);
      if (line.endsWith("\r")) line = line.slice(0, -1);
      if (line.length > 0) onLine(line);
    }
  });

  stream.on("end", () => {
    buffer += decoder.end();
    if (buffer.length > 0) {
      onLine(buffer.endsWith("\r") ? buffer.slice(0, -1) : buffer);
    }
  });
}
