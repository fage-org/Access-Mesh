/**
 * 成员表重置密码互斥回归锁（T-FE-068 重入批）。
 * 场景（外部持续审查探针实证，2026-10-14）：同一用户重置密码「确认→请求→弹新密码」
 * 全程无互斥——第一流在途时再次触发同用户重置，两请求并发写服务端；先写后回的
 * 迟到响应交付已失效密码（服务端真密码已是后写的那次）。
 *
 * 锁①：同用户二次触发入口被拒——第一流在途（请求未回）时再 reset 同一用户：
 *      零二次请求 + warning 提示（旧实现照发第二次请求必红）。
 * pending 复位后可再次触发；跨操作员后续重置仍可能使已返回密码失效。
 * 特征锁：不同用户不互斥 / 取消路径（confirm reject "cancel"）清理 pending 且无
 *         错误提示 / 上下文漂移守卫（T-FE-059）语义保持。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { defer } from "@/test-support/async";

const { mockMessage } = vi.hoisted(() => ({
  mockMessage: vi.fn()
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));

import { createResetPasswordFlow } from "./reset-password";

const TARGET = { id: 5, name: "张三" };

function createHarness(orgId: unknown = 1) {
  const confirmReset = vi.fn().mockResolvedValue(undefined);
  const requestReset = vi.fn();
  const deliverPassword = vi.fn().mockResolvedValue(undefined);
  let currentOrgId: unknown = orgId;
  const notifyContextDrift = vi.fn();
  const flow = createResetPasswordFlow({
    confirmReset,
    requestReset,
    deliverPassword,
    currentOrgId: () => currentOrgId,
    notifyContextDrift
  });
  return {
    flow,
    confirmReset,
    requestReset,
    deliverPassword,
    notifyContextDrift,
    setOrgId: (v: unknown) => (currentOrgId = v)
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("重置密码同用户互斥（T-FE-068）", () => {
  it("锁①：第一流在途时同用户二次触发被拒——零二次请求+warning 提示（旧实现第二次照发请求必红）", async () => {
    const h = createHarness();
    const d = defer<{ newPassword: string }>();
    h.requestReset.mockImplementationOnce(() => d.promise);

    const first = h.flow.reset(TARGET);
    await Promise.resolve(); // 走过 confirm（同步 resolve）
    expect(h.flow.isPending(TARGET.id)).toBe(true);

    const second = h.flow.reset(TARGET); // 在途期间再触发同一用户
    await second;
    for (let i = 0; i < 5; i++) await Promise.resolve(); // 第一流确认后发出请求
    expect(h.requestReset).toHaveBeenCalledTimes(1); // 旧实现：2 次 → 红
    expect(mockMessage).toHaveBeenCalledWith(
      "该用户的密码重置正在进行中，请稍候",
      { type: "warning" }
    ); // 旧实现：无互斥提示 → 红

    d.resolve({ newPassword: "P1" });
    await first;
    expect(h.deliverPassword).toHaveBeenCalledWith("P1"); // 唯一流照常交付
    expect(h.flow.isPending(TARGET.id)).toBe(false);
  });

  it("锁②：pending 复位后可再次触发（顺序触发不误伤）——两轮各一次请求、各交付各自密码", async () => {
    const h = createHarness();
    h.requestReset
      .mockResolvedValueOnce({ newPassword: "P1" })
      .mockResolvedValueOnce({ newPassword: "P2" });

    await h.flow.reset(TARGET);
    expect(h.flow.isPending(TARGET.id)).toBe(false);
    await h.flow.reset(TARGET);

    expect(h.requestReset).toHaveBeenCalledTimes(2);
    expect(h.deliverPassword).toHaveBeenNthCalledWith(1, "P1");
    expect(h.deliverPassword).toHaveBeenNthCalledWith(2, "P2");
  });

  it("特征锁：不同用户不互斥——两用户并发各自成流", async () => {
    const h = createHarness();
    const dA = defer<{ newPassword: string }>();
    const dB = defer<{ newPassword: string }>();
    h.requestReset.mockImplementationOnce(() => dA.promise);
    h.requestReset.mockImplementationOnce(() => dB.promise);

    const a = h.flow.reset({ id: 1, name: "A" });
    const b = h.flow.reset({ id: 2, name: "B" }); // A 在途，不同用户照常放行
    for (let i = 0; i < 5; i++) await Promise.resolve(); // 两流各自走过 confirm 发出请求
    expect(h.requestReset).toHaveBeenCalledTimes(2);

    dA.resolve({ newPassword: "PA" });
    dB.resolve({ newPassword: "PB" });
    await Promise.all([a, b]);
    expect(h.deliverPassword).toHaveBeenCalledWith("PA");
    expect(h.deliverPassword).toHaveBeenCalledWith("PB");
  });

  it('特征锁：取消路径（confirm reject "cancel"）——不发请求、无错误提示、pending 复位', async () => {
    const h = createHarness();
    h.confirmReset.mockRejectedValueOnce("cancel");

    await h.flow.reset(TARGET);

    expect(h.requestReset).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled(); // "cancel" 不弹「重置密码失败」
    expect(h.flow.isPending(TARGET.id)).toBe(false);
  });

  it("特征锁：上下文漂移守卫（T-FE-059）——确认框期间选中组织已变，不发请求+漂移提示（既有语义不因抽出丢失）", async () => {
    const h = createHarness(1);
    h.confirmReset.mockImplementationOnce(async () => {
      h.setOrgId(2); // 确认期间切换组织
    });

    await h.flow.reset(TARGET);

    expect(h.requestReset).not.toHaveBeenCalled();
    expect(h.notifyContextDrift).toHaveBeenCalledTimes(1);
    expect(h.flow.isPending(TARGET.id)).toBe(false);
  });

  it("服务端拒绝重叠重置时展示具体原因，释放本页 pending 且不交付密码", async () => {
    const h = createHarness();
    h.requestReset.mockRejectedValueOnce(
      new Error("该用户的密码重置正在进行中，请稍后重试")
    );

    await h.flow.reset(TARGET);

    expect(mockMessage).toHaveBeenCalledWith(
      "该用户的密码重置正在进行中，请稍后重试",
      {
        type: "error"
      }
    );
    expect(h.flow.isPending(TARGET.id)).toBe(false);
    expect(h.deliverPassword).not.toHaveBeenCalled();
  });
});
