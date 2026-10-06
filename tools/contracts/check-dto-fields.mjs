import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "../../frontend/node_modules/typescript/lib/typescript.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const manifest = JSON.parse(fs.readFileSync(path.join(root, "tools/contracts/dto-fields.json"), "utf8"));
const files = [...new Set(manifest.flatMap(group => group.targets)
  .filter(target => target.kind === "typescript").map(target => path.join(root, target.file)))];
const program = ts.createProgram(files, {
  target: ts.ScriptTarget.ESNext, moduleResolution: ts.ModuleResolutionKind.Bundler,
  module: ts.ModuleKind.ESNext, skipLibCheck: true, baseUrl: path.join(root, "frontend"),
  paths: { "@/*": ["src/*"] }
});
const checker = program.getTypeChecker();

// 只抽取 record 组件，不读取方法体；括号/泛型/注解/字符串里的逗号不作为组件分隔符。
function javaFields(file, name) {
  const source = fs.readFileSync(file, "utf8").replace(
    /\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g,
    token => token.startsWith("/") ? "" : token
  );
  const record = new RegExp(`\\brecord\\s+${name}\\s*(?:<[^>]*>)?\\s*\\(`).exec(source);
  if (!record) throw new Error(`record ${name} not found in ${file}`);
  let start = record.index + record[0].length;
  let depth = 0;
  let quote = null;
  const fields = [];
  for (let i = start; i < source.length; i++) {
    const c = source[i];
    if (quote) {
      if (c === "\\") i++;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === '"' || c === "'") { quote = c; continue; }
    if ((c === "," || c === ")") && depth === 0) {
      const component = source.slice(start, i).trim();
      if (component) {
        const field = /([\w$]+)\s*$/.exec(component)?.[1];
        if (!field) throw new Error(`Cannot parse record component: ${component}`);
        fields.push(field);
      }
      if (c === ")") return fields.sort();
      start = i + 1;
    } else if ("(<[{".includes(c)) depth++;
    else if (")>]}".includes(c)) depth--;
  }
  throw new Error(`Unterminated record ${name}`);
}

function fields(target) {
  const file = path.join(root, target.file);
  if (target.kind === "java") return javaFields(file, target.name);
  if (target.kind === "markdown") {
    const row = fs.readFileSync(file, "utf8").split(/\r?\n/)
      .find(line => line.startsWith(`| \`${target.name}\` |`));
    if (!row) throw new Error(`Documented DTO ${target.name} not found: ${file}`);
    return row.split("|")[2].trim().split(", ").sort();
  }
  if (target.kind !== "typescript") throw new Error(`Unknown kind: ${target.kind}`);
  const source = program.getSourceFile(file);
  if (!source) throw new Error(`TypeScript file not found: ${file}`);
  const module = checker.getSymbolAtLocation(source);
  const symbol = module && checker.getExportsOfModule(module).find(s => s.name === target.name);
  if (!symbol) throw new Error(`TypeScript export ${target.name} not found: ${file}`);
  return checker.getPropertiesOfType(checker.getDeclaredTypeOfSymbol(symbol)).map(s => s.name).sort();
}

let failures = 0;
if (process.argv.includes("--print-table")) {
  console.log("| DTO | 字段集合 |\n|---|---|");
  for (const group of manifest) console.log(`| \`${group.name}\` | ${fields(group.targets[0]).join(", ")} |`);
  process.exit(0);
}
for (const group of manifest) {
  if (group.targets.length < 2) throw new Error(`At least two targets required: ${group.name}`);
  const expected = fields(group.targets[0]);
  for (const target of group.targets.slice(1)) {
    const actual = fields(target);
    if (JSON.stringify(actual) === JSON.stringify(expected)) continue;
    failures++;
    console.error(`${group.name}: ${target.file}#${target.name}`);
    console.error(`  missing: ${expected.filter(field => !actual.includes(field)).join(", ") || "-"}`);
    console.error(`  extra: ${actual.filter(field => !expected.includes(field)).join(", ") || "-"}`);
  }
}
if (failures) {
  console.error(`DTO field comparison failed: ${failures} mismatches`);
  process.exitCode = 1;
} else console.log(`DTO field comparison passed: ${manifest.length} registered groups`);
