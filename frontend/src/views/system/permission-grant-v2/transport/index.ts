/**
 * V2 transport 入口 -- 按 dev/prod 环境返回可变授权 transport。
 *
 * 隔离机制（docs/plans/permission-grant-v2-plan.md mock 隔离方案）：
 * - dev 服务 或 演示构建（VITE_ENABLE_PROD_MOCK=true）：内存 transport（dev-mock.ts），
 *   不经全局 http client，绕开 fake-server 对 /api/perm/role-resource-permission/* 的接管。
 * - 真实部署构建（VITE_ENABLE_PROD_MOCK=false）：http transport（prod-http.ts），走真实后端。
 * - 环境判定用 import.meta.env.DEV || VITE_ENABLE_PROD_MOCK，与 build/plugins.ts
 *   fake-server enableProd 同源，确保演示构建 V2 与 fake-server 行为一致。
 *
 * 模块级懒初始化单例：同一环境只创建一次 transport 实例。
 * VITE_ENABLE_PROD_MOCK 为运行时值，minifier 无法静态剔除 dev 分支；演示构建需 dev-mock
 * 提供方案 A 数据，故不强制 tree-shake。
 */
import type { V2GrantTransport } from "./types";
import { createDevMockTransport } from "./dev-mock";
import { createProdHttpTransport } from "./prod-http";

let _dev: V2GrantTransport | null = null;
let _prod: V2GrantTransport | null = null;

/**
 * 是否使用内存 transport：
 * - dev 服务 或 演示构建（VITE_ENABLE_PROD_MOCK=true）-> 内存 transport
 * - 真实部署构建（VITE_ENABLE_PROD_MOCK=false）-> prod HTTP
 * 运行时 import.meta.env.VITE_ENABLE_PROD_MOCK 为字符串（Vite env 原始值），用 String() 防御。
 */
function useInMemoryTransport(): boolean {
  if (import.meta.env.DEV) return true;
  return String(import.meta.env.VITE_ENABLE_PROD_MOCK) === "true";
}

/** 按环境返回 transport（模块级单例） */
export function useV2GrantTransport(): V2GrantTransport {
  if (useInMemoryTransport()) {
    if (!_dev) _dev = createDevMockTransport();
    return _dev;
  }
  if (!_prod) _prod = createProdHttpTransport();
  return _prod;
}

export type { V2GrantTransport } from "./types";
