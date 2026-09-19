/**
 * 强制改密页表单规则锁（T-FE-046）。
 * 长度边界 8-32 对齐契约 §7.7 ResetPasswordReq（前端先拦、后端 @Size 兜底）；
 * confirmPassword 一致性校验器经工厂注入的 getPassword 取实时新密码。
 */
import { describe, it, expect } from "vitest";
import { buildChangePasswordRules } from "./rules";

describe("buildChangePasswordRules（T-FE-046）", () => {
  const rules = buildChangePasswordRules(() => "NewPass@2026");

  it("新密码长度边界 8-32：规则声明 min=8/max=32 与必填（契约 §7.7）", () => {
    const newPassword = rules.newPassword as Array<Record<string, unknown>>;
    expect(newPassword).toHaveLength(3);
    expect(newPassword[0]).toMatchObject({ required: true });
    expect(newPassword[2]).toMatchObject({ min: 8, max: 32 });
  });

  it("新密码纯空白拦截：规则声明 whitespace=true——8 个空格可过 required 与 min/max，后端 isBlank 判真走随机密码替换把用户锁死（claude 外评 P3 修复锁，删该规则必红）", () => {
    const newPassword = rules.newPassword as Array<Record<string, unknown>>;
    expect(newPassword[1]).toMatchObject({ whitespace: true });
  });

  it("确认密码一致时通过（callback 无参）", () => {
    const confirm = rules.confirmPassword as Array<Record<string, unknown>>;
    const validator = confirm[1].validator as (
      rule: unknown,
      value: string,
      callback: (error?: Error) => void
    ) => void;
    validator({}, "NewPass@2026", error => {
      expect(error).toBeUndefined();
    });
  });

  it("确认密码不一致时拒绝（callback 携带错误——两次输入不一致）", () => {
    const confirm = rules.confirmPassword as Array<Record<string, unknown>>;
    const validator = confirm[1].validator as (
      rule: unknown,
      value: string,
      callback: (error?: Error) => void
    ) => void;
    validator({}, "Different@2026", error => {
      expect(error).toBeInstanceOf(Error);
      expect((error as Error).message).toContain("不一致");
    });
  });

  it("工厂取实时新密码：表单改密码后按新值判定一致性（非工厂闭包旧值）", () => {
    let current = "First@12345";
    const dynamic = buildChangePasswordRules(() => current);
    const confirm = dynamic.confirmPassword as Array<Record<string, unknown>>;
    const validator = confirm[1].validator as (
      rule: unknown,
      value: string,
      callback: (error?: Error) => void
    ) => void;
    current = "Second@12345";
    validator({}, "Second@12345", error => {
      expect(error).toBeUndefined();
    });
  });
});
