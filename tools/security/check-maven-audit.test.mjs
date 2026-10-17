import test from "node:test";
import assert from "node:assert/strict";
import { evaluateMavenRisk } from "./check-maven-audit.mjs";

const dependency = () => ({
  fileName: "docker-java-transport-zerodep-3.7.1.jar (shaded HttpCore)",
  filePath: "/repository/docker-java-transport-zerodep-3.7.1.jar/META-INF/maven/org.apache.httpcomponents.core5/httpcore5/pom.xml",
  packages: [{ id: "pkg:maven/org.apache.httpcomponents.core5/httpcore5@5.3.6" }],
  projectReferences: ["Access Service:test", "E2E Tests:test"],
  suppressedVulnerabilities: [{ name: "CVE-2026-54399" }],
});
const evaluate = d => evaluateMavenRisk({ dependencies: [d] }, ["3.7.1"]);

test("accepts only the approved test scopes, with Windows or Unix report paths", () => {
  assert.equal(evaluate(dependency()).acceptedRisks.length, 1);
  const d = dependency(); d.filePath = d.filePath.replaceAll("/", "\\");
  assert.equal(evaluate(d).acceptedRisks.length, 1);
  const h2 = dependency(); h2.filePath = h2.filePath.replace("/httpcore5/", "/httpcore5-h2/");
  h2.packages[0].id = "pkg:maven/org.apache.httpcomponents.core5/httpcore5-h2@5.3.6";
  assert.equal(evaluate(h2).acceptedRisks.length, 1);
});
test("blocks production, unknown and missing scope evidence", () => {
  for (const refs of [["Access Service:compile"], ["New Module:test"], [], undefined]) {
    const d = dependency(); d.projectReferences = refs;
    assert.throws(() => evaluate(d), /approved test dependency paths/);
  }
});
test("does not exempt a normal HttpCore JAR or different shaded version", () => {
  const d = dependency(); d.filePath = "/repository/httpcore5-5.3.6.jar";
  assert.throws(() => evaluate(d), /approved test dependency paths/);
  d.filePath = dependency().filePath; d.packages[0].id = "pkg:maven/org.apache.httpcomponents.core5/httpcore5@5.3.5";
  assert.throws(() => evaluate(d), /approved test dependency paths/);
});
test("new stable release or missing registry evidence blocks, prerelease does not", () => {
  const report = { dependencies: [dependency()] };
  for (const version of ["3.7.2", "3.8.0", "4.0.0"]) {
    assert.throws(() => evaluateMavenRisk(report, ["3.7.1", version]), /New stable/);
  }
  assert.throws(() => evaluateMavenRisk(report, []), /Cannot verify/);
  assert.equal(evaluateMavenRisk(report, ["3.7.1", "4.0.0-rc1"]).acceptedRisks.length, 1);
});
test("rejects absent reports and needs no exception after the affected dependency is removed", () => {
  assert.throws(() => evaluateMavenRisk({}, []), /Missing Maven/);
  assert.deepEqual(evaluateMavenRisk({ dependencies: [{ fileName: "safe.jar" }] }, []), { acceptedRisks: [] });
});
