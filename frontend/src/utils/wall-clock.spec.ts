import { describe, it, expect } from "vitest";
import { formatToWallClockIso } from "./wall-clock";

/**
 * 墙钟序列化共享 util 规格测试（T-PERM-032 抽取；操作日志/变更日志两页共用）。
 *
 * 数字对齐语义：取 Date 的本地墙钟分量（而非 UTC 分量）——el-date-picker 按浏览器
 * 本地分量构造 Date，表格原样展示后端 UTC 墙钟串，序列化取本地分量使
 * 「用户输入的数字 == 提交的数字 == 表格展示的数字」。
 * 以下用例以固定本地时间构造 Date（非 UTC 等值），若实现误取 UTC 分量
 * （getUTC* 系，参照系错位）即失败——为 T-PERM-025 codex 评审纠偏结论的回归锁。
 */
describe("formatToWallClockIso", () => {
  it("取本地墙钟分量输出 ISO 无偏移串（补零）", () => {
    // 本地 2026-08-29 09:05:03（无论时区，分量即用户所见）
    const d = new Date(2026, 7, 29, 9, 5, 3);
    expect(formatToWallClockIso(d)).toBe("2026-08-29T09:05:03");
  });

  it("月份/日期/时分秒单位数补零", () => {
    const d = new Date(2026, 0, 2, 3, 4, 5);
    expect(formatToWallClockIso(d)).toBe("2026-01-02T03:04:05");
  });

  it("本地 10:00 序列化为 10:00（误取 UTC 分量则数字错位，回归锁）", () => {
    const d = new Date(2026, 7, 29, 10, 0, 0);
    const out = formatToWallClockIso(d);
    expect(out).toBe("2026-08-29T10:00:00");
    // 与 UTC 分量实现区分：当本地时区非 UTC 时，UTC 实现必然产出不同数字
    const utcVariant = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}T${String(d.getUTCHours()).padStart(2, "0")}:00:00`;
    if (d.getTimezoneOffset() !== 0) {
      expect(out).not.toBe(utcVariant);
    }
  });
});
