import { responseSchema } from "./schema";
import type { ExtractRequest } from "./validate";

const API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

export type GeminiResult =
  | { ok: true; text: string }
  | { ok: false; kind: "rate_limited" | "upstream_error" };

/** The model's own safety filter refused the input: treated as an off-topic answer. */
export const BLOCKED_ANSWER = JSON.stringify({ input_kind: "off_topic", tasks: [] });

/** Fills {now} and {timezone} in the prompt kept in the SYSTEM_PROMPT secret. */
export function renderPrompt(template: string, request: ExtractRequest): string {
  return template.replaceAll("{now}", request.now).replaceAll("{timezone}", request.timezone);
}

/**
 * The transcript is data, not instructions: it goes in the user turn between delimiters, and any
 * delimiter the speaker "said" is removed so it cannot close the block early.
 */
export function wrapTranscript(transcript: string): string {
  const cleaned = transcript.replace(/<\/?\s*transcript\s*>/gi, " ");
  return `<transcript>\n${cleaned}\n</transcript>`;
}

export function buildGeminiBody(systemPrompt: string, request: ExtractRequest) {
  return {
    systemInstruction: { parts: [{ text: renderPrompt(systemPrompt, request) }] },
    contents: [{ role: "user", parts: [{ text: wrapTranscript(request.transcript) }] }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema,
      temperature: 0.2,
    },
  };
}

interface GeminiResponse {
  promptFeedback?: { blockReason?: string };
  candidates?: Array<{
    finishReason?: string;
    content?: { parts?: Array<{ text?: string }> };
  }>;
}

/** Pulls the JSON text out of a generateContent response. */
export function extractText(response: GeminiResponse): GeminiResult {
  if (response.promptFeedback?.blockReason) return { ok: true, text: BLOCKED_ANSWER };

  const candidate = response.candidates?.[0];
  if (candidate?.finishReason === "SAFETY") return { ok: true, text: BLOCKED_ANSWER };

  const text = candidate?.content?.parts?.map((part) => part.text ?? "").join("") ?? "";
  return text.trim().length > 0 ? { ok: true, text } : { ok: false, kind: "upstream_error" };
}

export async function callGemini(
  apiKey: string,
  model: string,
  systemPrompt: string,
  request: ExtractRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<GeminiResult> {
  let response: Response;
  try {
    response = await fetchImpl(`${API_BASE}/${encodeURIComponent(model)}:generateContent`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify(buildGeminiBody(systemPrompt, request)),
    });
  } catch {
    return { ok: false, kind: "upstream_error" };
  }

  if (response.status === 429) return { ok: false, kind: "rate_limited" };
  if (!response.ok) {
    // Status only; the body may echo the request.
    console.error(`Gemini responded ${response.status}`);
    return { ok: false, kind: "upstream_error" };
  }

  try {
    return extractText((await response.json()) as GeminiResponse);
  } catch {
    return { ok: false, kind: "upstream_error" };
  }
}
