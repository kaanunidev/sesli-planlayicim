import { describe, expect, it } from "vitest";
import { MAX_TASKS, parseExtractRequest, validateAiOutput } from "../src/validate";

const validRequest = {
  transcript: "Yarın sabah onda maç var",
  now: "2026-10-11T21:40:00+03:00",
  timezone: "Europe/Istanbul",
  locale: "tr-TR",
};

describe("parseExtractRequest", () => {
  it("accepts the contract example and trims the transcript", () => {
    expect(parseExtractRequest({ ...validRequest, transcript: "  Yarın maç var  " })).toEqual({
      ...validRequest,
      transcript: "Yarın maç var",
    });
  });

  it.each([
    ["not an object", "hello"],
    ["array", [validRequest]],
    ["missing transcript", { ...validRequest, transcript: undefined }],
    ["blank transcript", { ...validRequest, transcript: "   " }],
    ["too long transcript", { ...validRequest, transcript: "a".repeat(2001) }],
    ["now without offset", { ...validRequest, now: "2026-10-11T21:40:00" }],
    ["now not a date", { ...validRequest, now: "yarın" }],
    ["bad timezone", { ...validRequest, timezone: "Europe/Istanbul; DROP" }],
    ["bad locale", { ...validRequest, locale: "türkçe" }],
  ])("rejects %s", (_, body) => {
    expect(parseExtractRequest(body)).toBeNull();
  });

  it("accepts exactly 2000 characters", () => {
    expect(parseExtractRequest({ ...validRequest, transcript: "a".repeat(2000) })).not.toBeNull();
  });
});

const task = {
  title: "Maç izle",
  date: "2026-10-12",
  time: "10:00",
  duration_minutes: 120,
  reminder_text: "Maç birazdan başlıyor",
  missing: [],
};

describe("validateAiOutput", () => {
  it("passes a valid plan through", () => {
    expect(validateAiOutput(JSON.stringify({ input_kind: "plan", tasks: [task] }))).toEqual({
      input_kind: "plan",
      tasks: [task],
    });
  });

  it("drops unknown fields", () => {
    const output = validateAiOutput(
      JSON.stringify({ input_kind: "plan", extra: 1, tasks: [{ ...task, emoji: "x" }] }),
    );
    expect(output).toEqual({ input_kind: "plan", tasks: [task] });
  });

  it("empties tasks for off_topic and empty", () => {
    expect(validateAiOutput(JSON.stringify({ input_kind: "off_topic", tasks: [task] }))?.tasks).toEqual([]);
    expect(validateAiOutput(JSON.stringify({ input_kind: "empty", tasks: [task] }))?.tasks).toEqual([]);
  });

  it(`cuts the list at ${MAX_TASKS} tasks`, () => {
    const many = Array.from({ length: 20 }, () => task);
    expect(validateAiOutput(JSON.stringify({ input_kind: "plan", tasks: many }))?.tasks).toHaveLength(MAX_TASKS);
  });

  it("keeps only date/time in missing and nulls a non-integer duration", () => {
    const output = validateAiOutput(
      JSON.stringify({ input_kind: "plan", tasks: [{ ...task, duration_minutes: 1.5, missing: ["time", "x"] }] }),
    );
    expect(output?.tasks[0]).toMatchObject({ duration_minutes: null, missing: ["time"] });
  });

  it.each([
    ["not JSON", "Elbette! İşte görevler"],
    ["array", "[]"],
    ["unknown input_kind", JSON.stringify({ input_kind: "maybe", tasks: [] })],
    ["tasks not a list", JSON.stringify({ input_kind: "plan", tasks: "Maç" })],
    ["task without title", JSON.stringify({ input_kind: "plan", tasks: [{ ...task, title: 5 }] })],
    ["date of wrong type", JSON.stringify({ input_kind: "plan", tasks: [{ ...task, date: 20261012 }] })],
  ])("rejects %s", (_, text) => {
    expect(validateAiOutput(text)).toBeNull();
  });
});
