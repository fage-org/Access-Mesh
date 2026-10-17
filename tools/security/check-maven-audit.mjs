import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const acceptedCve = "CVE-2026-54399";
const allowedReferences = new Set(["Access Service:test", "E2E Tests:test"]);
const metadataUrl = "https://repo.maven.apache.org/maven2/com/github/docker-java/docker-java-transport-zerodep/maven-metadata.xml";

// Dependency Check XML selectors cannot constrain Maven scopes. Keep this accepted
// risk separate from false positives and verify the resolved scope in the raw report.
export function evaluateMavenRisk(report, publishedVersions) {
  if (!Array.isArray(report.dependencies) || report.dependencies.length === 0) {
    throw new Error("Missing Maven dependency report");
  }
  const accepted = [];
  for (const dependency of report.dependencies) {
    if (!dependency.suppressedVulnerabilities?.some(v => v.name === acceptedCve)) continue;
    if (!/[/\\]docker-java-transport-zerodep-3\.7\.1\.jar[/\\]META-INF[/\\]maven[/\\]org\.apache\.httpcomponents\.core5[/\\]httpcore5(?:-h2)?[/\\]pom\.xml$/.test(dependency.filePath)
      || !dependency.packages?.some(p => /^pkg:maven\/org\.apache\.httpcomponents\.core5\/httpcore5(?:-h2)?@5\.3\.6$/.test(p.id))
      || !Array.isArray(dependency.projectReferences)
      || dependency.projectReferences.length === 0
      || !dependency.projectReferences.every(ref => allowedReferences.has(ref))) {
      throw new Error("Docker HttpCore risk exception is outside its approved test dependency paths");
    }
    if (!Array.isArray(publishedVersions) || !publishedVersions.includes("3.7.1")) {
      throw new Error("Cannot verify published docker-java versions");
    }
    if (publishedVersions.some(version => {
      const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version);
      return match && (+match[1] > 3 || (+match[1] === 3 &&
        (+match[2] > 7 || (+match[2] === 7 && +match[3] > 1))));
    })) {
      throw new Error("New stable docker-java release requires review of the temporary risk exception");
    }
    accepted.push({ cve: acceptedCve, file: dependency.fileName, references: dependency.projectReferences });
  }
  return { acceptedRisks: accepted };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const reportPath = process.argv[2];
    const report = JSON.parse(readFileSync(reportPath, "utf8"));
    const requiresException = report.dependencies?.some(d =>
      d.suppressedVulnerabilities?.some(v => v.name === acceptedCve));
    let versions = [];
    if (requiresException) {
      const response = await fetch(metadataUrl, { signal: AbortSignal.timeout(30000) });
      if (!response.ok) throw new Error(`Maven Central metadata returned ${response.status}`);
      versions = [...(await response.text()).matchAll(/<version>([^<]+)<\/version>/g)].map(m => m[1]);
    }
    const decision = { ...evaluateMavenRisk(report, versions), checkedAt: new Date().toISOString(), metadataUrl };
    writeFileSync(`${reportPath}.decision.json`, `${JSON.stringify(decision, null, 2)}\n`);
    console.log(JSON.stringify(decision));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
