// Input and output checks, see docs/AI_CONTRACT.md → "Alakasız girdi ve prompt injection".

export const MAX_BODY_BYTES = 8 * 1024;
export const MAX_TRANSCRIPT_CHARS = 2000;
export const MAX_TASKS = 15;

export const INPUT_KINDS = ["plan", "empty", "off_topic"] as const;
export type InputKind = (typeof INPUT_KINDS)[number];

export interface ExtractRequest {
  transcript: string;
  now: string;
  timezone: string;
  locale: string;
}

export interface AiTask {
  title: string;
  date: string | null;
  time: string | null;
  duration_minutes: number | null;
  reminder_text: string | null;
  missing: string[];
}

export interface AiOutput {
  input_kind: InputKind;
  tasks: AiTask[];
}

const NOW_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})$/;
const TIMEZONE_PATTERN = /^[A-Za-z0-9_+\-/]{1,64}$/;
const LOCALE_PATTERN = /^[a-z]{2,3}(-[A-Z]{2})?$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Returns the request when every field is present and sane, otherwise null (→ 400). */
export function parseExtractRequest(body: unknown): ExtractRequest | null {
  if (!isRecord(body)) return null;
  const { transcript, now, timezone, locale } = body;
  if (typeof transcript !== "string" || typeof now !== "string") return null;
  if (typeof timezone !== "string" || typeof locale !== "string") return null;

  const trimmed = transcript.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_TRANSCRIPT_CHARS) return null;
  if (!NOW_PATTERN.test(now) || Number.isNaN(Date.parse(now))) return null;
  if (!TIMEZONE_PATTERN.test(timezone) || !LOCALE_PATTERN.test(locale)) return null;

  return { transcript: trimmed, now, timezone, locale };
}

function stringOrNull(value: unknown): string | null | undefined {
  if (value === null || value === undefined) return null;
  return typeof value === "string" ? value : undefined;
}

function toTask(value: unknown): AiTask | null {
  if (!isRecord(value) || typeof value.title !== "string") return null;
  const date = stringOrNull(value.date);
  const time = stringOrNull(value.time);
  const reminder = stringOrNull(value.reminder_text);
  if (date === undefined || time === undefined || reminder === undefined) return null;

  const duration = value.duration_minutes;
  const durationMinutes = typeof duration === "number" && Number.isInteger(duration) ? duration : null;
  const missing = Array.isArray(value.missing)
    ? value.missing.filter((m): m is string => m === "date" || m === "time")
    : [];

  return {
    title: value.title,
    date,
    time,
    duration_minutes: durationMinutes,
    reminder_text: reminder,
    missing,
  };
}

/**
 * Parses the model's JSON and rebuilds it with only the contract's fields.
 * Returns null when it is not the expected shape (→ 502 bad_ai_response).
 * More than [MAX_TASKS] tasks are cut, not rejected.
 */
export function validateAiOutput(text: string): AiOutput | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (!isRecord(parsed)) return null;

  const kind = parsed.input_kind;
  if (typeof kind !== "string" || !(INPUT_KINDS as readonly string[]).includes(kind)) return null;

  const rawTasks = parsed.tasks ?? [];
  if (!Array.isArray(rawTasks)) return null;

  const tasks: AiTask[] = [];
  for (const raw of rawTasks.slice(0, MAX_TASKS)) {
    const task = toTask(raw);
    if (task === null) return null;
    tasks.push(task);
  }

  return { input_kind: kind as InputKind, tasks: kind === "plan" ? tasks : [] };
}
