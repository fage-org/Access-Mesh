import { expect, it } from "vitest";
import { refreshFrameUrl } from "./frame-url";

it.each([
  ["/app#/home", "", "#/home"],
  ["/app?tenant=demo#/home", "demo", "#/home"],
  ["https://child.example/app?t=1&tenant=demo#report", "demo", "#report"]
])("刷新 %s 将参数放在查询串并保留路由", (source, tenant, hash) => {
  const result = new URL(refreshFrameUrl(source, "https://parent.example", 42));
  expect(result.searchParams.getAll("t")).toEqual(["42"]);
  expect(result.searchParams.get("tenant") ?? "").toBe(tenant);
  expect(result.hash).toBe(hash);
  expect(result.origin).toBe(
    source.startsWith("https:")
      ? "https://child.example"
      : "https://parent.example"
  );
});
