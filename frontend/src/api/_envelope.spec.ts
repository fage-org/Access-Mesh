/**
 * 统一错误文案来源回归（T-FE-051）：
 * 非 2xx 响应优先消费后端 body message（R 信封），替换 axios 默认
 * 「Request failed with status code 403」；无 body 文案回落 fallback，不透出 axios 默认串。
 */
import { describe, it, expect } from "vitest";
import { RequestError, toErrorMessage } from "./_envelope";

/** axios 非 2xx 错误形态（AxiosError 关键字段 duck-type，spec 用纯对象等价构造） */
function axiosError(bodyMessage?: string) {
  return {
    message: "Request failed with status code 403",
    response: { status: 403, data: bodyMessage ? { message: bodyMessage } : {} }
  };
}

describe("toErrorMessage（T-FE-051 错误文案统一）", () => {
  it("非 2xx：优先后端 body message（旧 e.message 直透会显 axios 默认串）", () => {
    expect(toErrorMessage(axiosError("无访问权限"), "加载失败")).toBe(
      "无访问权限"
    );
  });

  it("非 2xx 无 body 文案：回落 fallback，不透出 axios 默认 message", () => {
    expect(toErrorMessage(axiosError(), "加载系统配置失败")).toBe(
      "加载系统配置失败"
    );
  });

  it("非 2xx body message 为空白串：视同无文案回落 fallback", () => {
    expect(toErrorMessage(axiosError("   "), "加载失败")).toBe("加载失败");
  });

  it("response:null 形态：不抛 TypeError，回落 fallback（双轨评审 P3 回归锁）", () => {
    expect(
      toErrorMessage(
        { message: "Request failed with status code 403", response: null },
        "加载失败"
      )
    ).toBe("加载失败");
  });

  it("业务错（RequestError）：透出 message（unwrap 已带后端文案）", () => {
    const e = new RequestError("不能删除当前登录用户", {
      appCode: 40010,
      kind: "business"
    });
    expect(toErrorMessage(e, "删除失败")).toBe("不能删除当前登录用户");
  });

  it("普通 Error：透出 message（网络断「Network Error」等既有语义不变）", () => {
    expect(toErrorMessage(new Error("Network Error"), "加载失败")).toBe(
      "Network Error"
    );
  });

  it("非 Error 值（如确认弹窗 cancel 字符串）：fallback", () => {
    expect(toErrorMessage("cancel", "操作失败")).toBe("操作失败");
    expect(toErrorMessage(null, "操作失败")).toBe("操作失败");
  });
});
