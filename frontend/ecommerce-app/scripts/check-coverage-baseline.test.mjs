import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import process from "node:process";
import test from "node:test";
import { fileURLToPath, URL } from "node:url";

const checkerPath = fileURLToPath(new URL("./check-coverage-baseline.mjs", import.meta.url));

test("rejects a coverage report below its recorded module baseline", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const baselinePath = join(directory, "baseline.json");
  const reportPath = join(directory, "coverage-final.json");
  const baseline = {
    schemaVersion: 1,
    modules: {
      "src/example.ts": {
        lines: { covered: 8, total: 10 },
        branches: { covered: 4, total: 5 },
      },
    },
    criticalFiles: {},
  };
    const report = {
      "src/example.ts": {
      s: { 1: 1, 2: 0 },
      b: { 1: [1, 1] },
      statementMap: {
        1: { start: { line: 1 }, end: { line: 1 } },
        2: { start: { line: 2 }, end: { line: 2 } },
      },
       branchMap: {
         1: {
           locations: [
             { start: { line: 1 }, end: { line: 1 } },
             { start: { line: 1 }, end: { line: 1 } },
           ],
         },
       },
    },
  };

  writeFileSync(baselinePath, JSON.stringify(baseline));
  writeFileSync(reportPath, JSON.stringify(report));

  assert.throws(
    () =>
      execFileSync(
        process.execPath,
        [checkerPath, reportPath, baselinePath],
        { encoding: "utf8", stdio: "pipe" },
      ),
    /below baseline/,
  );
});

test("rejects equal-percentage coverage with reduced covered and total counters", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const baselinePath = join(directory, "baseline.json");
  const reportPath = join(directory, "coverage-final.json");
  writeFileSync(
    baselinePath,
    JSON.stringify({
      schemaVersion: 1,
      modules: {
        "src/example.ts": {
          lines: { covered: 4, total: 8 },
          branches: { covered: 2, total: 4 },
        },
      },
      criticalFiles: {},
    }),
  );
  writeFileSync(
    reportPath,
    JSON.stringify({
      "src/example.ts": {
        s: { 1: 1, 2: 0, 3: 1, 4: 0 },
        statementMap: {
          1: { start: { line: 1 }, end: { line: 1 } },
          2: { start: { line: 2 }, end: { line: 2 } },
          3: { start: { line: 3 }, end: { line: 3 } },
          4: { start: { line: 4 }, end: { line: 4 } },
        },
        b: { 1: [1, 0] },
         branchMap: {
           1: {
             locations: [
               { start: { line: 1 }, end: { line: 1 } },
               { start: { line: 1 }, end: { line: 1 } },
             ],
           },
         },
      },
    }),
  );

  assert.throws(
    () => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { encoding: "utf8", stdio: "pipe" }),
    /below baseline/,
  );
});

test("reports malformed coverage data as a controlled validation error", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const baselinePath = join(directory, "baseline.json");
  const reportPath = join(directory, "coverage-final.json");
  writeFileSync(
    baselinePath,
    JSON.stringify({ schemaVersion: 1, modules: {}, criticalFiles: {} }),
  );
  writeFileSync(reportPath, JSON.stringify({ "src/example.ts": {} }));

  assert.throws(
    () => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { encoding: "utf8", stdio: "pipe" }),
    /Invalid coverage report/,
  );
});

test("reports malformed critical-file definitions as a controlled validation error", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const baselinePath = join(directory, "baseline.json");
  const reportPath = join(directory, "coverage-final.json");
  writeFileSync(reportPath, JSON.stringify({}));
  writeFileSync(
    baselinePath,
    JSON.stringify({
      schemaVersion: 1,
      modules: {},
      criticalFiles: {
        checkout: { files: "src/checkout.ts", threshold: { lines: 1.1, branches: 0 } },
      },
    }),
  );

  assert.throws(
    () => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { encoding: "utf8", stdio: "pipe" }),
    /Invalid criticalFiles/,
  );
});

function writeFixture(directory, baseline, report) {
  const baselinePath = join(directory, "baseline.json");
  const reportPath = join(directory, "coverage-final.json");
  writeFileSync(baselinePath, JSON.stringify(baseline));
  writeFileSync(reportPath, JSON.stringify(report));
  return [reportPath, baselinePath];
}

function simpleReport() {
  return {
    "src/critical.ts": {
      s: { 1: 1, 2: 0 },
      b: { 1: [1, 0] },
      statementMap: {
        1: { start: { line: 1 }, end: { line: 1 } },
        2: { start: { line: 2 }, end: { line: 2 } },
      },
      branchMap: {
        1: {
          locations: [
            { start: { line: 3 }, end: { line: 3 } },
            { start: { line: 3 }, end: { line: 3 } },
          ],
        },
      },
    },
  };
}

test("accepts a critical-file threshold met by measured counters", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const [reportPath, baselinePath] = writeFixture(
    directory,
    {
      schemaVersion: 1,
      modules: {},
      criticalFiles: { critical: { files: ["src/critical.ts"], threshold: { lines: 0.5, branches: 0.5 } } },
    },
    simpleReport(),
  );

  assert.doesNotThrow(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }));
});

test("rejects a critical-file threshold missed by measured counters", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const [reportPath, baselinePath] = writeFixture(
    directory,
    {
      schemaVersion: 1,
      modules: {},
      criticalFiles: { critical: { files: ["src/critical.ts"], threshold: { lines: 0.6, branches: 0.6 } } },
    },
    simpleReport(),
  );

  assert.throws(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }), /below threshold/);
});

test("rejects a report missing a configured critical file", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const [reportPath, baselinePath] = writeFixture(
    directory,
    {
      schemaVersion: 1,
      modules: {},
      criticalFiles: { critical: { files: ["src/missing.ts"], threshold: { lines: 0, branches: 0 } } },
    },
    simpleReport(),
  );

  assert.throws(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }), /missing from the report/);
});

test("rejects branch locations with malformed entries or mismatched counters", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const baseline = { schemaVersion: 1, modules: {}, criticalFiles: {} };
  const malformed = simpleReport();
  malformed["src/critical.ts"].branchMap[1].locations[0] = {};
  let [reportPath, baselinePath] = writeFixture(directory, baseline, malformed);
  assert.throws(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }), /branch/);

  const mismatched = simpleReport();
  mismatched["src/critical.ts"].b[1] = [1];
  [reportPath, baselinePath] = writeFixture(directory, baseline, mismatched);
  assert.throws(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }), /branch/);
});

test("requires numeric line and branch thresholds", () => {
  const directory = mkdtempSync(join(tmpdir(), "coverage-baseline-"));
  const report = simpleReport();
  for (const threshold of [{ lines: null, branches: 0 }, { lines: 0 }, { lines: 0, branches: 2 }]) {
    const [reportPath, baselinePath] = writeFixture(directory, {
      schemaVersion: 1,
      modules: {},
      criticalFiles: { critical: { files: ["src/critical.ts"], threshold } },
    }, report);
    assert.throws(() => execFileSync(process.execPath, [checkerPath, reportPath, baselinePath], { stdio: "pipe" }), /Invalid criticalFiles threshold/);
  }
});
