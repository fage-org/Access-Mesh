/**
 * 业务域页域配置加载竞态回归（外部评审收口）：
 * 切域时旧请求晚归不得把 A 域配置回写到 B 域（旧实现无请求序号守卫，慢响应直接覆盖 configData，
 * 且 B 标题下编辑/删除会操作 A 的配置值与 ID）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const getDomainConfigList = vi.fn();

// 阻断 hook 模块级的 store/router/element-plus 链（同 conflict-rule hook.spec 范式）
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));
vi.mock("@/utils/auth", () => ({ hasPerms: () => true }));
vi.mock("@/utils/message", () => ({ message: vi.fn() }));
vi.mock("@/api/biz-domain", () => ({}));
vi.mock("@/api/domain-config", () => ({
  getDomainConfigList: (...args: unknown[]) => getDomainConfigList(...args)
}));

import { useBizDomain } from "./hook";

function domain(code: string) {
  return { id: 1, code, name: code, global: false } as any;
}

describe("业务域页域配置加载竞态", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("先选 A 再选 B、A 晚归：configData 只保留 B 的配置（旧实现被 A 覆盖必失败）", async () => {
    const pending: Array<(v: any) => void> = [];
    getDomainConfigList.mockImplementation(
      () => new Promise(resolve => pending.push(resolve))
    );

    const hook = useBizDomain();
    hook.selectDomain(domain("A"));
    hook.selectDomain(domain("B"));
    // 切域立即清空旧域数据
    expect(hook.configData.value).toEqual([]);

    // B（最新请求）先完成
    pending[1]({ items: [{ id: 20, configType: "CLASSIFY" }] });
    await Promise.resolve();
    expect(getDomainConfigList).toHaveBeenLastCalledWith("B");
    expect(hook.configData.value).toEqual([{ id: 20, configType: "CLASSIFY" }]);

    // A（过期请求）晚归被丢弃，不覆盖 B、不误清 loading
    pending[0]({ items: [{ id: 10, configType: "SUB_PERM" }] });
    await Promise.resolve();
    expect(hook.configData.value).toEqual([{ id: 20, configType: "CLASSIFY" }]);
    expect(hook.configLoading.value).toBe(false);
  });

  it("过期请求失败不弹错不清数据（旧实现会误报误清）", async () => {
    const resolvers: Array<(v: any) => void> = [];
    const rejecters: Array<(v: any) => void> = [];
    getDomainConfigList.mockImplementation(
      () =>
        new Promise((resolve, reject) => {
          resolvers.push(resolve);
          rejecters.push(reject);
        })
    );

    const hook = useBizDomain();
    hook.selectDomain(domain("A"));
    hook.selectDomain(domain("B"));
    resolvers[1]({ items: [{ id: 21 }] });
    await Promise.resolve();
    rejecters[0](new Error("stale A failed"));
    await Promise.resolve();

    expect(hook.configData.value).toEqual([{ id: 21 }]);
    expect(hook.configLoading.value).toBe(false);
  });
});
