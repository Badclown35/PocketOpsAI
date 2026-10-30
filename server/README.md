# PocketOps Claude proxy (starter)

The Android app must never contain an Anthropic API key. This small Node.js
server is a placeholder for the private proxy that will call Claude.

Requirements: Node.js 20+.

Set environment variables:
- ANTHROPIC_API_KEY
- CLAUDE_MODEL (choose a model enabled for your API account)

Run:
    node server.mjs

The `/analyze` endpoint is intentionally not wired to the APK yet. Before
deployment, add authentication, per-user rate limits, request-size limits,
and a strict JSON schema validator. Do not expose this server publicly as-is.
