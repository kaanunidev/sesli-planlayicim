import { callGemini } from "./gemini";
import { MAX_BODY_BYTES, parseExtractRequest, validateAiOutput } from "./validate";

interface RateLimiter {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

export interface Env {
  GEMINI_API_KEY?: string;
  SYSTEM_PROMPT?: string;
  GEMINI_MODEL: string;
  RATE_LIMITER?: RateLimiter;
}

const ROUTE = "/v1/extract";

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

const error = (status: number, code: string, headers?: Record<string, string>) =>
  json(status, { error: code }, headers);

/** Never logs or returns the transcript; only status codes are logged. */
export async function handle(request: Request, env: Env, fetchImpl: typeof fetch = fetch): Promise<Response> {
  const url = new URL(request.url);
  if (url.pathname !== ROUTE) return error(404, "not_found");
  if (request.method !== "POST") return error(405, "method_not_allowed", { allow: "POST" });

  const declaredLength = Number(request.headers.get("content-length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) return error(413, "payload_too_large");
  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) return error(413, "payload_too_large");

  if (env.RATE_LIMITER) {
    const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
    const { success } = await env.RATE_LIMITER.limit({ key: ip });
    if (!success) return error(429, "rate_limited", { "retry-after": "60" });
  }

  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return error(400, "invalid_input");
  }
  const extractRequest = parseExtractRequest(body);
  if (extractRequest === null) return error(400, "invalid_input");

  if (!env.GEMINI_API_KEY || !env.SYSTEM_PROMPT) {
    console.error("GEMINI_API_KEY or SYSTEM_PROMPT is not set");
    return error(500, "not_configured");
  }

  const result = await callGemini(env.GEMINI_API_KEY, env.GEMINI_MODEL, env.SYSTEM_PROMPT, extractRequest, fetchImpl);
  if (!result.ok) {
    return result.kind === "rate_limited"
      ? error(429, "rate_limited", { "retry-after": "60" })
      : error(502, "upstream_error");
  }

  const output = validateAiOutput(result.text);
  if (output === null) {
    console.error("Model output failed validation");
    return error(502, "bad_ai_response");
  }
  return json(200, output);
}

export default {
  fetch: (request: Request, env: Env) => handle(request, env),
};
