const { spawn } = require("child_process");
const { StringDecoder } = require("string_decoder");
const { WebSocketServer } = require("ws");
const fs = require("fs");
const path = require("path");
const readline = require("readline");

const PORT = 8080;
const SESSION_BASE = path.join(process.env.HOME || "/root", ".pi/agent/sessions");

const wss = new WebSocketServer({ port: PORT });

console.log(`Pi WebSocket bridge listening on ws://0.0.0.0:${PORT}`);

wss.on("connection", (ws, req) => {
  const clientAddr = req.socket.remoteAddress;
  console.log(`[+] Client connected: ${clientAddr}`);

  // Spawn pi in RPC mode for this connection
  const pi = spawn("pi", ["--mode", "rpc"], {
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

  // WebSocket → pi stdin (forward commands, or handle server-side commands)
  ws.on("message", (data) => {
    const msg = data.toString();
    console.log(`[>] ${msg.substring(0, 120)}`);

    try {
      const cmd = JSON.parse(msg);

      // Server-side commands (not part of pi RPC)
      if (cmd.type === "list_sessions") {
        handleListSessions(ws, cmd);
        return;
      }

      // Forward everything else to pi
      if (!pi.stdin.destroyed) {
        pi.stdin.write(msg + "\n");
      }
    } catch (e) {
      // Not valid JSON, forward raw
      if (!pi.stdin.destroyed) {
        pi.stdin.write(msg + "\n");
      }
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

// --- Server-side: list sessions ---

async function handleListSessions(ws, cmd) {
  try {
    const sessions = await scanSessions();
    const response = {
      type: "response",
      command: "list_sessions",
      success: true,
      data: { sessions },
    };
    if (cmd.id) response.id = cmd.id;
    ws.send(JSON.stringify(response));
  } catch (e) {
    const response = {
      type: "response",
      command: "list_sessions",
      success: false,
      error: e.message,
    };
    if (cmd.id) response.id = cmd.id;
    ws.send(JSON.stringify(response));
  }
}

async function scanSessions() {
  const sessions = [];

  if (!fs.existsSync(SESSION_BASE)) return sessions;

  const cwdDirs = fs.readdirSync(SESSION_BASE);
  for (const cwdDir of cwdDirs) {
    const dirPath = path.join(SESSION_BASE, cwdDir);
    if (!fs.statSync(dirPath).isDirectory()) continue;

    const files = fs.readdirSync(dirPath).filter((f) => f.endsWith(".jsonl"));
    for (const file of files) {
      const filePath = path.join(dirPath, file);
      try {
        const info = await parseSessionFile(filePath, cwdDir);
        if (info) sessions.push(info);
      } catch (e) {
        // skip corrupt files
      }
    }
  }

  // Sort newest first
  sessions.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
  return sessions;
}

async function parseSessionFile(filePath, cwdDir) {
  const stream = fs.createReadStream(filePath, { encoding: "utf8" });
  const rl = readline.createInterface({ input: stream, crlfDelay: Infinity });

  let header = null;
  let firstUserMessage = null;
  let sessionName = null;
  let messageCount = 0;
  let lastTimestamp = null;
  let linesRead = 0;

  for await (const line of rl) {
    if (!line.trim()) continue;
    linesRead++;

    try {
      const obj = JSON.parse(line);

      if (obj.type === "session" && !header) {
        header = obj;
      }

      if (obj.type === "session_name") {
        sessionName = obj.name || null;
      }

      if (obj.type === "message" && obj.message) {
        if (obj.message.role === "user") {
          messageCount++;
          if (!firstUserMessage) {
            let content = obj.message.content || "";
            if (Array.isArray(content)) {
              const textBlock = content.find((b) => b.type === "text");
              content = textBlock ? textBlock.text : "";
            }
            firstUserMessage = content.substring(0, 120);
          }
        }
        lastTimestamp = obj.timestamp || lastTimestamp;
      }
    } catch (e) {
      // skip malformed lines
    }

    // Don't read entire huge files just for metadata
    if (linesRead > 500 && firstUserMessage) break;
  }

  rl.close();
  stream.destroy();

  if (!header) return null;

  return {
    id: header.id,
    path: filePath,
    timestamp: header.timestamp,
    lastActivity: lastTimestamp || header.timestamp,
    cwd: cwdDir.replace(/--/g, "/"),
    name: sessionName,
    preview: firstUserMessage || "(empty session)",
    messageCount,
  };
}

// --- JSONL reader ---

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
