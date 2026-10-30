import http from "node:http";

const PORT = Number(process.env.PORT || 8787);
const HOST = "127.0.0.1";
const MAX_BODY_BYTES = 200_000;
const MAX_APPS = 1000;

const PROTECTED_PACKAGES = new Set([
  "android",
  "com.android.systemui",
  "com.android.phone",
  "com.android.server.telecom",
  "com.android.providers.telephony",
  "com.android.providers.contacts",
  "com.android.providers.settings",
  "com.android.settings",
  "com.android.packageinstaller",
  "com.google.android.packageinstaller",
  "com.android.permissioncontroller",
  "com.google.android.permissioncontroller",
  "com.google.android.gms",
  "com.google.android.gsf",
  "com.android.bluetooth",
  "com.android.location.fused",
  "com.android.launcher3",
  "com.android.providers.downloads",
  "com.android.networkstack",
  "com.android.networkstack.tethering",
  "com.android.cellbroadcastreceiver",
  "com.android.emergency",
  "com.google.android.cellbroadcastservice",
  "com.google.android.apps.messaging",
  "com.android.mms",
  "com.google.android.inputmethod.latin"
]);

const PROTECTED_PACKAGE_PATTERN =
  /(^|\.)(systemui|telephony|telecom|permissioncontroller|packageinstaller|bluetooth|networkstack)(\.|$)/i;

const PROTECTED_LABEL_PATTERN =
  /\b(phone|dialer|sms|messaging|bluetooth|location|settings|launcher|system ui|google play services|package installer|permission controller|emergency|notification|carrier services|connectivity)\b/i;

function sendJson(res, status, data) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(data));
}

function classifyApp(app) {
  const packageName = app.packageName;
  const label = app.label;
  const protectedByPackage =
    PROTECTED_PACKAGES.has(packageName.toLowerCase()) ||
    PROTECTED_PACKAGE_PATTERN.test(packageName);
  const protectedByLabel = PROTECTED_LABEL_PATTERN.test(label);

  if (protectedByPackage || protectedByLabel) {
    return {
      packageName,
      label,
      risk: "high",
      reason: "This may be an essential Android, phone, connectivity, launcher, notification, or Google service. Keep it unless its exact role is verified.",
      action: "keep"
    };
  }

  if (app.isSystem) {
    return {
      packageName,
      label,
      risk: "medium",
      reason: "This is marked as a system app. Its dependencies and device-specific role are unknown, so do not remove it based on this scan.",
      action: "review_only"
    };
  }

  return {
    packageName,
    label,
    risk: "low",
    reason: "This is not identified as a protected component, but its purpose and dependencies cannot be confirmed from its name alone.",
    action: "review_only"
  };
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    return sendJson(res, 200, {
      ok: true,
      configured: true,
      provider: "local",
      model: "pocketops-rules-v1"
    });
  }

  if (req.method !== "POST" || req.url !== "/analyze") {
    return sendJson(res, 404, { error: "not_found" });
  }

  let raw = "";
  let bodyTooLarge = false;

  try {
    for await (const chunk of req) {
      raw += chunk;
      if (Buffer.byteLength(raw, "utf8") > MAX_BODY_BYTES) {
        bodyTooLarge = true;
        break;
      }
    }
  } catch {
    return sendJson(res, 400, { error: "request_read_failed" });
  }

  if (bodyTooLarge) {
    rep.destroy();
    return;
  }

  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    return sendJson(res, 400, { error: "invalid_json" });
  }

  if (!payload || !Array.isArray(payload.apps) || payload.apps.length > MAX_APPS) {
    return sendJson(res, 400, { error: "invalid_app_inventory" });
  }

  const safeApps = payload.apps.map((item) => ({
    packageName: String(item?.packageName || "").slice(0, 180),
    label: String(item?.label || "").slice(0, 100),
    isSystem: Boolean(item?.isSystem)
  }));

  const recommendations = safeApps.map(classifyApp);
  const protectedCount = recommendations.filter((item) => item.action === "keep").length;
  const systemCount = safeApps.filter((item) => item.isSystem).length;
  const reviewCount = recommendations.filter((item) => item.action === "review_only").length;

  const analysis = {
    provider: "local",
    model: "pocketops-rules-v1",
    summary: `Scanned ${safeApps.length} apps. ${protectedCount} were marked keep, ${systemCount} are marked as system apps, and ${reviewCount} require human review. This is a rule-based screening result, not an AI judgment or a guarantee that any app is safe to remove.`,
    recommendations
  };

  return sendJson(res, 200, { analysis: JSON.stringify(analysis) });
});

server.listen(PORT, HOST, () => {
  console.log(`PocketOps local analyzer listening on http://${HOST}:${PORT}`);
});
