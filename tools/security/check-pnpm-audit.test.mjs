import { test } from "node:test";
import assert from "node:assert/strict";
import { evaluateAudit } from "./check-pnpm-audit.mjs";

function report(overrides = {}) {
  return {
    metadata: { vulnerabilities: { high: 1, critical: 0 } },
    advisories: {
      example: {
        github_advisory_id: "GHSA-vfj7-8cjw-p6xm",
        module_name: "braces",
        severity: "high",
        findings: [
          {
            version: "3.0.3",
            dev: true,
            paths: [".>sass>@parcel/watcher>micromatch>braces"],
          },
        ],
        ...overrides,
      },
    },
  };
}

test("accepts only the approved development exception without deleting the raw finding", () => {
  const raw = report();
  assert.deepEqual(evaluateAudit(raw, 1, ["3.0.3"]).accepted, [
    "GHSA-vfj7-8cjw-p6xm",
  ]);
  assert.equal(raw.metadata.vulnerabilities.high, 1);
});

test("blocks a production path, a new dependency root, and a changed version", () => {
  for (const finding of [
    { version: "3.0.3", dev: false, paths: [".>sass>micromatch>braces"] },
    { version: "3.0.3", dev: true, paths: [".>new-tool>micromatch>braces"] },
    { version: "3.0.2", dev: true, paths: [".>sass>micromatch>braces"] },
  ])
    assert.equal(
      evaluateAudit(report({ findings: [finding] }), 1, ["3.0.3"]).blocked
        .length,
      1,
    );
});

test("a newly published stable release expires the exception", () => {
  assert.equal(
    evaluateAudit(report(), 1, ["3.0.3", "3.0.4"]).blocked.length,
    1,
  );
});

test("other high advisories and escalation to critical remain blocking", () => {
  assert.equal(
    evaluateAudit(report({ github_advisory_id: "GHSA-other" }), 1, ["3.0.3"])
      .blocked.length,
    1,
  );
  const raw = report({ severity: "critical" });
  raw.metadata.vulnerabilities = { high: 0, critical: 1 };
  assert.equal(evaluateAudit(raw, 1, ["3.0.3"]).blocked.length, 1);
});

test("registry errors, failed commands and incomplete reports fail closed", () => {
  assert.throws(() =>
    evaluateAudit({ error: "registry unavailable" }, 1, ["3.0.3"]),
  );
  assert.throws(() => evaluateAudit(report(), 2, ["3.0.3"]));
  assert.throws(() => evaluateAudit(report(), 1, null));
  assert.throws(() => evaluateAudit(report(), 1, []));
  const raw = report();
  raw.advisories = {};
  assert.throws(() => evaluateAudit(raw, 1, ["3.0.3"]));
});
