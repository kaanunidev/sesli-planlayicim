import { describe, expect, it } from "vitest";
import { type Env, handle } from "../src/index";

const URL_EXTRACT = "https://proxy.test/v1/extract";

const validBody = {
  transcript: "Yarın onda maç var",
  now: "2026-10-11T21:40:00+03:00",
  timezone: "Europe/Istanbul",
  locale: "tr-TR",
};

const modelAnswer = {
  input_kind: "plan",
  tasks: [
    { title: "Maç izle", date: "2026-10-12", time: "10:00", duration_minutes: null, reminder_text: null, missing: [] },
  ],
};

function geminiReturning(text: string) {
  return (async () =>
    new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }))) as unknown as typeof fetch;
}

const env = (overrides: Partial<Env> = {}): Env => ({
  GEMINI_API_KEY: "test-key",
  SYSTEM_PROMPT: "Şu an: {now}",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
  ...overrides,
});

const post = (body: unknown, path = URL_EXTRACT) =>
  new Request(path, { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body) });

describe("handle", () => {
  it("returns the validated model answer", async () => {
    const response = await handle(post(validBody), env(), geminiReturning(JSON.stringify(modelAnswer)));
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual(modelAnswer);
  });

  it("404 for other paths, 405 for other methods", async () => {
    expect((await handle(post(validBody, "https://proxy.test/"), env())).status).toBe(404);
    expect((await handle(new Request(URL_EXTRACT), env())).status).toBe(405);
  });

  it("400 for broken JSON or a bad request", async () => {
    expect((await handle(post("{nope"), env())).status).toBe(400);
    expect((await handle(post({ ...validBody, transcript: "" }), env())).status).toBe(400);
  });

  it("413 for a large body", async () => {
    expect((await handle(post({ ...validBody, transcript: "a".repeat(9000) }), env())).status).toBe(413);
  });

  it("429 when the rate limiter says no", async () => {
    const limited = env({ RATE_LIMITER: { limit: async () => ({ success: false }) } });
    expect((await handle(post(validBody), limited)).status).toBe(429);
  });

  it("500 when secrets are missing, without calling Gemini", async () => {
    let called = false;
    const spy = (async () => {
      called = true;
      return new Response("{}");
    }) as unknown as typeof fetch;
    expect((await handle(post(validBody), env({ SYSTEM_PROMPT: undefined }), spy)).status).toBe(500);
    expect(called).toBe(false);
  });

  it("502 when the model answers outside the contract", async () => {
    const response = await handle(post(validBody), env(), geminiReturning("Elbette! İşte görevler"));
    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({ error: "bad_ai_response" });
  });
});
