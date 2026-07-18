import { defineConfig } from "vitest/config";
import { alias } from "./build/utils";

/**
 * Vitest 配置（T-FE-030 首个前端测试任务，建立基础设施）。
 * 仅复用 Vite 的 alias（@/@build），不加载 fake-server 等完整 plugins。
 * 纯函数测试用 node 环境；后续组件测试可扩展 environment。
 */
export default defineConfig({
  resolve: { alias },
  test: {
    environment: "node",
    include: ["src/**/*.{test,spec}.ts"],
    coverage: {
      provider: "v8",
      include: ["src/views/system/permission-grant-v2/utils/**/*.ts"]
    }
  }
});
