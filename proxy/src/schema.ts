// Gemini structured output schema (OpenAPI subset) mirroring docs/AI_CONTRACT.md.
// The model cannot answer in free text; the proxy still validates the result.

const nullableString = (description: string) => ({ type: "STRING", nullable: true, description });

export const responseSchema = {
  type: "OBJECT",
  properties: {
    input_kind: { type: "STRING", enum: ["plan", "empty", "off_topic"] },
    tasks: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          title: { type: "STRING", description: "Kısa, emir kipinde, en fazla 60 karakter" },
          date: nullableString("YYYY-MM-DD veya null"),
          time: nullableString("HH:mm (24 saat) veya null"),
          duration_minutes: { type: "INTEGER", nullable: true },
          reminder_text: nullableString("Samimi, kısa, en fazla 120 karakter"),
          missing: { type: "ARRAY", items: { type: "STRING", enum: ["date", "time"] } },
        },
        required: ["title", "date", "time", "duration_minutes", "reminder_text", "missing"],
        propertyOrdering: ["title", "date", "time", "duration_minutes", "reminder_text", "missing"],
      },
    },
  },
  required: ["input_kind", "tasks"],
  propertyOrdering: ["input_kind", "tasks"],
} as const;
