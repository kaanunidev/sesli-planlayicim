// Copies system-prompt.txt into the SYSTEM_PROMPT entry of .dev.vars for `wrangler dev`.
// Other lines (GEMINI_API_KEY) are left untouched; nothing is printed except a status line.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const promptPath = `${root}system-prompt.txt`;
const devVarsPath = `${root}.dev.vars`;

if (!existsSync(promptPath)) {
  console.error("system-prompt.txt not found");
  process.exit(1);
}
const prompt = readFileSync(promptPath, "utf8").trim();
if (prompt.includes("`")) {
  // Backticks quote the multi-line value in .dev.vars.
  console.error("system-prompt.txt must not contain backticks");
  process.exit(1);
}

const current = existsSync(devVarsPath) ? readFileSync(devVarsPath, "utf8") : "";
const withoutPrompt = current.replace(/^SYSTEM_PROMPT=`[\s\S]*?`[ \t]*\r?\n?/m, "").trimEnd();
const next = `${withoutPrompt ? `${withoutPrompt}\n` : ""}SYSTEM_PROMPT=\`${prompt}\`\n`;
writeFileSync(devVarsPath, next, "utf8");

console.log(`SYSTEM_PROMPT updated in .dev.vars (${prompt.length} chars)`);
