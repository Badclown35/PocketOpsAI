import http from "node:http";

const PORT = Number(process.env.PORT || 8787);
const API_KEY = process.env.ANTHROPIC_API_KEY;
const MODEL = process.env.CLAUDE_MODEL;

const server = http.createServer(async (req, res) => {
  res.setHeader("content-type", "application/json; charset=utf-8");
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200);
    return res.end(JSON.stringify({ ok: true, configured: Boolean(API_KEY && MODEL) }));
  }

  if (req.method !== "POST" || req.url !== "/analyze") {
    res.writeHead(404);
    return res.end(JSON.stringify({ error: "not_found" }));
  }
  if (!API_KEY || !MODEL) {
    res.writeHead(503);
    return res.end(JSON.stringify({ error: "server_not_configured" }));
  }

  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 200_000) {
      res.writeHead(413);
      return res.end(JSON.stringify({ error: "request_too_large" }));
    }
  }

  let payload;
  try { payload = JSON.parse(raw); }
  catch {
    res.writeHead(400);
    return res.end(JSON.stringify({ error: "invalid_json" }));
  }

  // Only accept a minimized inventory, never personal file contents.
  if (!Array.isArray(payload.apps) || payload.apps.length > 1000) {
    res.writeHead(400);
    return res.end(JSON.stringify({ error: "invalid_app_inventory" }));
  }

  const safeApps = payload.apps.map(a => ({
    packageName: String(a.packageName || "").slice(0, 180),
    label: String(a.label || "").slice(0, 100),
    isSystem: Boolean(a.isSystem)
  }));

  try {
    const response = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": API_KEY,
        "anthropic-version": "2023-06-01"
      },
      body: JSON.stringify({
        model: MODEL,
        max_tokens: 1800,
        system: "You are a cautious Android app inventory reviewer. Do not claim certainty from package names alone. Never recommend removing core telephony, SMS, launcher, System UI, package installer, permission controller, Google Play services, connectivity, Bluetooth, location, or notification components. Return concise analysis only; no shell commands and no automatic actions.",
        messages: [{
          role: "user",
          content: "Review this app inventory and classify candidates for human review. Return JSON with keys summary and recommendations. Each recommendation must include packageName, risk (low|medium|high), reason, and action (review_only|keep). Inventory: " + JSON.stringify(safeApps)
        }]
      })
    });

    const result = await response.json();
    if (!response.ok) {
      res.writeHead(502);
      return res.end(JSON.stringify({ error: "claude_api_error", status: response.status }));
    }
    const text = (result.content || []).filter(x => x.type === "text").map(x => x.text).join("\n");
    res.writeHead(200);
    return res.end(JSON.stringify({ analysis: text }));
  } catch {
    res.writeHead(502);
    return res.end(JSON.stringify({ error: "upstream_unavailable" }));
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`PocketOps proxy listening on 127.0.0.1:${PORT}`);
});
