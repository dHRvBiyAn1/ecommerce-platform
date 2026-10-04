import { readFileSync } from "node:fs";
import process from "node:process";
import { fileURLToPath, URL } from "node:url";

const appDirectory = fileURLToPath(new URL("..", import.meta.url));
const [, , reportArgument, baselineArgument] = process.argv;
const reportPath = reportArgument ?? `${appDirectory}/coverage/coverage-final.json`;
const baselinePath = baselineArgument ?? `${appDirectory}/coverage-baseline.json`;

function readJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch (error) {
    throw new Error(`Invalid JSON in ${filePath}: ${error.message}`);
  }
}

function moduleCounters(coverage) {
  const lineCounts = new Map();
  for (const [statementId, location] of Object.entries(coverage.statementMap ?? {})) {
    const count = coverage.s?.[statementId] ?? 0;
    for (let line = location.start.line; line <= location.end.line; line += 1) {
      lineCounts.set(line, Math.max(lineCounts.get(line) ?? 0, count));
    }
  }

  const lineValues = [...lineCounts.values()];
  const branchValues = Object.values(coverage.b ?? {}).flat();
  return {
    lines: {
      covered: lineValues.filter((value) => value > 0).length,
      total: lineValues.length,
    },
    branches: {
      covered: branchValues.filter((value) => value > 0).length,
      total: branchValues.length,
    },
  };
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function validateCounters(counters, label) {
  if (!isRecord(counters)) throw new Error(`Invalid ${label} counters`);
  for (const kind of ["lines", "branches"]) {
    const value = counters[kind];
    if (
      !isRecord(value) ||
      !Number.isInteger(value.covered) ||
      !Number.isInteger(value.total) ||
      value.covered < 0 ||
      value.covered > value.total
    ) {
      throw new Error(`Invalid ${kind} counters for ${label}`);
    }
  }
}

function validateCoverageReport(coverage) {
  if (!isRecord(coverage)) throw new Error("Invalid coverage report: expected a module object");
  for (const [modulePath, fileCoverage] of Object.entries(coverage)) {
    if (
      !isRecord(fileCoverage) ||
      !isRecord(fileCoverage.statementMap) ||
      !isRecord(fileCoverage.s) ||
      !isRecord(fileCoverage.branchMap) ||
      !isRecord(fileCoverage.b)
    ) {
      throw new Error(`Invalid coverage report module: ${modulePath}`);
    }
    for (const location of Object.values(fileCoverage.statementMap)) {
      if (
        !isRecord(location) ||
        !isRecord(location.start) ||
        !isRecord(location.end) ||
        !Number.isInteger(location.start.line) ||
        !Number.isInteger(location.end.line) ||
        location.start.line < 1 ||
        location.end.line < location.start.line
      ) {
        throw new Error(`Invalid coverage report statement locations: ${modulePath}`);
      }
    }
    for (const [statementId, count] of Object.entries(fileCoverage.s)) {
      const location = fileCoverage.statementMap[statementId];
      if (
        !Number.isInteger(count) ||
        count < 0 ||
        !isRecord(location) ||
        !isRecord(location.start) ||
        !isRecord(location.end) ||
        !Number.isInteger(location.start.line) ||
        !Number.isInteger(location.end.line) ||
        location.start.line < 1 ||
        location.end.line < location.start.line
      ) {
        throw new Error(`Invalid coverage report statement data: ${modulePath}`);
      }
    }
    for (const branch of Object.values(fileCoverage.branchMap)) {
      if (!isRecord(branch) || !Array.isArray(branch.locations)) {
        throw new Error(`Invalid coverage report branch locations: ${modulePath}`);
      }
      for (const location of branch.locations) {
        if (
          !isRecord(location) ||
          !isRecord(location.start) ||
          !isRecord(location.end) ||
          !Number.isInteger(location.start.line) ||
          !Number.isInteger(location.end.line) ||
          location.start.line < 1 ||
          location.end.line < location.start.line
        ) {
          throw new Error(`Invalid coverage report branch locations: ${modulePath}`);
        }
      }
    }
    for (const [branchId, counts] of Object.entries(fileCoverage.b)) {
      if (
        !Array.isArray(counts) ||
        !counts.every((count) => Number.isInteger(count) && count >= 0) ||
        !isRecord(fileCoverage.branchMap[branchId]) ||
        counts.length !== fileCoverage.branchMap[branchId].locations.length
      ) {
        throw new Error(`Invalid coverage report branch data: ${modulePath}`);
      }
    }
    if (Object.keys(fileCoverage.branchMap).some((branchId) => !Object.hasOwn(fileCoverage.b, branchId))) {
      throw new Error(`Invalid coverage report branch data: ${modulePath}`);
    }
  }
}

function relativeModulePath(filePath) {
  const normalized = filePath.replaceAll("\\", "/");
  const marker = "/src/";
  const index = normalized.lastIndexOf(marker);
  return index >= 0 ? normalized.slice(index + 1) : normalized;
}

function percentage(value) {
  return value.total === 0 ? 1 : value.covered / value.total;
}

function checkCounter(errors, modulePath, kind, current, baseline) {
  if (
    current.covered < baseline.covered ||
    current.total < baseline.total ||
    percentage(current) < percentage(baseline)
  ) {
    errors.push(
      `${modulePath} ${kind} coverage is below baseline (${current.covered}/${current.total} < ${baseline.covered}/${baseline.total})`,
    );
  }
}

function checkCriticalFiles(errors, report, criticalFiles) {
  for (const [name, definition] of Object.entries(criticalFiles ?? {})) {
    const files = definition.files ?? [];
    const counters = files.map((file) => report[file]).filter(Boolean);
    if (counters.length !== files.length) {
      errors.push(`${name} critical-file coverage is missing from the report`);
      continue;
    }
    for (const kind of ["lines", "branches"]) {
      const threshold = definition.threshold?.[kind];
      if (typeof threshold !== "number") continue;
      const covered = counters.reduce((sum, value) => sum + value[kind].covered, 0);
      const total = counters.reduce((sum, value) => sum + value[kind].total, 0);
      if ((total === 0 ? 1 : covered / total) < threshold) {
        errors.push(`${name} ${kind} coverage is below threshold (${covered}/${total})`);
      }
    }
  }
}

function validateBaseline(baseline) {
  if (!isRecord(baseline) || baseline.schemaVersion !== 1 || !isRecord(baseline.modules)) {
    throw new Error("Unsupported coverage baseline schema");
  }
  for (const [modulePath, counters] of Object.entries(baseline.modules)) {
    validateCounters(counters, modulePath);
  }
  if (baseline.criticalFiles !== undefined && !isRecord(baseline.criticalFiles)) {
    throw new Error("Invalid criticalFiles: expected an object");
  }
  for (const [name, definition] of Object.entries(baseline.criticalFiles ?? {})) {
    if (
      !isRecord(definition) ||
      !Array.isArray(definition.files) ||
      definition.files.length === 0 ||
      !definition.files.every((file) => typeof file === "string" && file.length > 0)
    ) {
      throw new Error(`Invalid criticalFiles definition: ${name}`);
    }
    if (!isRecord(definition.threshold)) {
      throw new Error(`Invalid criticalFiles threshold: ${name}`);
    }
    for (const kind of ["lines", "branches"]) {
      const threshold = definition.threshold[kind];
      if (typeof threshold !== "number" || threshold < 0 || threshold > 1 || !Number.isFinite(threshold)) {
        throw new Error(`Invalid criticalFiles threshold: ${name}.${kind}`);
      }
    }
  }
}

const baseline = readJson(baselinePath);
const coverage = readJson(reportPath);
validateBaseline(baseline);
validateCoverageReport(coverage);
const report = Object.fromEntries(
  Object.entries(coverage).map(([filePath, fileCoverage]) => [
    relativeModulePath(filePath),
    moduleCounters(fileCoverage),
  ]),
);
const errors = [];

for (const [modulePath, expected] of Object.entries(baseline.modules ?? {})) {
  const actual = report[modulePath];
  if (!actual) {
    errors.push(`${modulePath} is missing from the coverage report`);
    continue;
  }
  checkCounter(errors, modulePath, "lines", actual.lines, expected.lines);
  checkCounter(errors, modulePath, "branches", actual.branches, expected.branches);
}

checkCriticalFiles(errors, report, baseline.criticalFiles);

if (errors.length > 0) {
  globalThis.console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  globalThis.console.log(`Coverage baseline passed for ${Object.keys(baseline.modules ?? {}).length} modules.`);
}
