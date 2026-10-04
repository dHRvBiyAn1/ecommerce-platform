import { spawnSync } from "node:child_process";
import process from "node:process";
import { fileURLToPath, URL } from "node:url";

const appDirectory = fileURLToPath(new URL("..", import.meta.url));
const commands = [
  ["npm", ["run", "typecheck"]],
  ["npm", ["run", "lint"]],
  ["npm", ["run", "test:coverage"]],
  [process.execPath, ["scripts/check-coverage-baseline.mjs"]],
  ["npm", ["exec", "--", "playwright", "test"]],
];

for (const [command, args] of commands) {
  const result = spawnSync(command, args, { cwd: appDirectory, stdio: "inherit" });
  if (result.status !== 0) process.exit(result.status ?? 1);
}
