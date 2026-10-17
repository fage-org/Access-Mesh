import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

// T-ACCESS-105: the accepted exception is limited to existing development paths.
const acceptedId = "GHSA-vfj7-8cjw-p6xm";
const developmentRoots = new Set([
  "@tailwindcss/vite",
  "@vitejs/plugin-vue",
  "@vitejs/plugin-vue-jsx",
  "eslint-plugin-vue",
  "lint-staged",
  "sass",
  "stylelint",
  "stylelint-config-recess-order",
  "stylelint-config-recommended-vue",
  "stylelint-config-standard-scss",
  "stylelint-prettier",
  "typescript-eslint",
  "vite",
  "vite-plugin-cdn-import",
  "vite-plugin-compression",
  "vitest",
]);

export function evaluateAudit(report, exitCode, publishedVersions) {
  if (
    ![0, 1].includes(exitCode) ||
    report.error ||
    !report.advisories ||
    !report.metadata?.vulnerabilities ||
    !Array.isArray(publishedVersions) ||
    !publishedVersions.length
  ) {
    throw new Error("Audit or registry query did not produce a valid report");
  }
  const advisories = Object.values(report.advisories);
  const high = advisories.filter((a) => a.severity === "high");
  const critical = advisories.filter((a) => a.severity === "critical");
  if (
    high.length !== report.metadata.vulnerabilities.high ||
    critical.length !== report.metadata.vulnerabilities.critical ||
    (exitCode === 1 && high.length + critical.length === 0)
  ) {
    throw new Error("Audit severity counts do not match its advisory details");
  }
  const newerRelease = publishedVersions.some((version) => {
    const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version);
    return (
      match &&
      (+match[1] > 3 ||
        (+match[1] === 3 &&
          (+match[2] > 0 || (+match[2] === 0 && +match[3] > 3))))
    );
  });
  const accepted = [];
  const blocked = [];
  for (const advisory of [...high, ...critical]) {
    const scopedException =
      advisory.github_advisory_id === acceptedId &&
      advisory.module_name === "braces" &&
      advisory.severity === "high" &&
      !newerRelease &&
      Array.isArray(advisory.findings) &&
      advisory.findings.length > 0 &&
      advisory.findings.every(
        (f) =>
          f.version === "3.0.3" &&
          f.dev === true &&
          Array.isArray(f.paths) &&
          f.paths.length > 0 &&
          f.paths.every((path) => {
            const parts = path.split(">");
            return (
              parts[0] === "." &&
              developmentRoots.has(parts[1]) &&
              parts.at(-2) === "micromatch" &&
              parts.at(-1) === "braces"
            );
          }),
      );
    (scopedException ? accepted : blocked).push(advisory.github_advisory_id);
  }
  return { accepted, blocked, newerBracesRelease: newerRelease };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  try {
    const reportPath = process.argv[2];
    const report = JSON.parse(
      readFileSync(reportPath, "utf8").replace(/^\uFEFF/, ""),
    );
    // Query the official registry: a newer stable release requires renewed review.
    const response = await fetch("https://registry.npmjs.org/braces", {
      signal: AbortSignal.timeout(30000),
    });
    if (!response.ok)
      throw new Error(`Registry returned HTTP ${response.status}`);
    const registry = await response.json();
    if (!registry.versions) throw new Error("Registry version list is missing");
    const result = evaluateAudit(
      report,
      Number(process.argv[3]),
      Object.keys(registry.versions),
    );
    writeFileSync(
      `${reportPath}.decision.json`,
      JSON.stringify(result, null, 2) + "\n",
    );
    console.log(JSON.stringify(result));
    process.exitCode = result.blocked.length ? 1 : 0;
  } catch (error) {
    console.error(`Security audit failed: ${error.message}`);
    process.exitCode = 1;
  }
}
