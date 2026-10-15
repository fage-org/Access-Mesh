import { describe, it, expect } from "vitest";
import {
  createEmptyRules,
  createEmptyItem,
  serializeRules,
  parseRules,
  summarizeRules,
  isValidCidrInput,
  CONDITION_TYPE_LABEL
} from "./condition-rules";

/**
 * condition-rules 共享纯函数测试。
 * 该模块被 ReConditionEditor 与 permission-condition 页共用，是权限条件规则
 * 序列化/反序列化/摘要的唯一事实源，需保持行为稳定。
 */
describe("condition-rules", () => {
  describe("createEmptyRules", () => {
    it("should return AND logic with empty items when called", () => {
      expect(createEmptyRules()).toEqual({ logic: "AND", items: [] });
    });
  });

  describe("createEmptyItem", () => {
    it("should init start/end params when type is DATE_RANGE", () => {
      const item = createEmptyItem("DATE_RANGE");
      expect(item.type).toBe("DATE_RANGE");
      expect(item.params).toEqual({ start: "", end: "" });
    });

    it("should init cidrs array when type is IP_WHITELIST", () => {
      const item = createEmptyItem("IP_WHITELIST");
      expect(item.type).toBe("IP_WHITELIST");
      expect(item.params).toEqual({ cidrs: [] });
    });

    it("should default to DATE_RANGE when type omitted", () => {
      const item = createEmptyItem();
      expect(item.type).toBe("DATE_RANGE");
      expect(item.params).toEqual({ start: "", end: "" });
    });

    it("should assign unique _id for each new item", () => {
      const a = createEmptyItem();
      const b = createEmptyItem();
      expect(a._id).not.toBe(b._id);
    });
  });

  describe("serializeRules", () => {
    it("should strip _id runtime field when serializing", () => {
      const rules = {
        logic: "AND" as const,
        items: [
          {
            _id: 1,
            type: "DATE_RANGE",
            params: { start: "2026-01-01", end: "2026-12-31" }
          }
        ]
      };
      const parsed = JSON.parse(serializeRules(rules));
      expect(parsed.items[0]).not.toHaveProperty("_id");
      expect(parsed.items[0]).toEqual({
        type: "DATE_RANGE",
        params: { start: "2026-01-01", end: "2026-12-31" }
      });
    });

    it("should output valid structure when items empty", () => {
      expect(
        JSON.parse(serializeRules({ logic: "OR" as const, items: [] }))
      ).toEqual({
        logic: "OR",
        items: []
      });
    });
  });

  describe("parseRules", () => {
    it("should parse valid JSON into structured rules", () => {
      const json = JSON.stringify({
        logic: "OR",
        items: [{ type: "IP_BLACKLIST", params: { cidrs: ["10.0.0.0/8"] } }]
      });
      const rules = parseRules(json);
      expect(rules.logic).toBe("OR");
      expect(rules.items).toHaveLength(1);
      expect(rules.items[0].type).toBe("IP_BLACKLIST");
      expect(rules.items[0].params.cidrs).toEqual(["10.0.0.0/8"]);
    });

    it("should return empty rules when json is null", () => {
      expect(parseRules(null)).toEqual({ logic: "AND", items: [] });
    });

    it("should return empty rules when json is undefined", () => {
      expect(parseRules(undefined)).toEqual({ logic: "AND", items: [] });
    });

    it("should return empty rules when json is invalid (fail-safe)", () => {
      expect(parseRules("not a json")).toEqual({ logic: "AND", items: [] });
    });

    it("should default logic to AND when missing", () => {
      expect(parseRules('{"items":[]}').logic).toBe("AND");
    });

    it("should fallback to DATE_RANGE when item type is not a string", () => {
      const rules = parseRules(
        '{"logic":"AND","items":[{"type":123,"params":{}}]}'
      );
      expect(rules.items[0].type).toBe("DATE_RANGE");
    });

    it("should keep unknown but valid string type as-is", () => {
      const rules = parseRules(
        '{"logic":"AND","items":[{"type":"UNKNOWN","params":{}}]}'
      );
      expect(rules.items[0].type).toBe("UNKNOWN");
    });
  });

  describe("summarizeRules", () => {
    it("should return 无规则 when items empty", () => {
      expect(summarizeRules(null)).toBe("无规则");
      expect(summarizeRules('{"logic":"AND","items":[]}')).toBe("无规则");
    });

    it("should include logic count and type labels when items present", () => {
      const json = JSON.stringify({
        logic: "AND",
        items: [
          { type: "DATE_RANGE", params: {} },
          { type: "IP_WHITELIST", params: {} }
        ]
      });
      expect(summarizeRules(json)).toBe(
        `AND · 2 项（${CONDITION_TYPE_LABEL.DATE_RANGE}、${CONDITION_TYPE_LABEL.IP_WHITELIST}）`
      );
    });
  });

  describe("isValidCidrInput（T-PERM-109 基础形态校验）", () => {
    it("should accept legit literals and prefixes", () => {
      for (const v of [
        "10.1.2.3",
        "0.0.0.0/0",
        "10.0.0.0/8",
        "192.168.1.0/32",
        "2001:db8::/32",
        "::1/128"
      ]) {
        expect(isValidCidrInput(v)).toBe(true);
      }
    });

    it("should enforce IPv6 structure and ASCII digits (外评 P2-2)", () => {
      // 结构非法但字符集合法的 IPv6 拒绝；合法结构（压缩/全组/v4 映射尾）保持
      expect(isValidCidrInput(":::/64")).toBe(false);
      expect(isValidCidrInput("::")).toBe(true);
      expect(isValidCidrInput("::1")).toBe(true);
      expect(isValidCidrInput("1:2:3:4:5:6:7:8:9")).toBe(false);
      // codex sol r2 P2-2：zone 一律拒绝（无 zone 匹配语义 + 解析平台相关），
      // 旧「fe80::1%eth0 合法」断言随之翻转（与 Java 单源同步）
      expect(isValidCidrInput("fe80::1%eth0/10")).toBe(false);
      expect(isValidCidrInput("fe80::1%1/10")).toBe(false);
      expect(isValidCidrInput("::ffff:192.168.1.0%1/120")).toBe(false);
      // 全角数字拒绝（JS \d 仅 ASCII，锁行为）
      expect(isValidCidrInput("１.２.３.４/24")).toBe(false);
      expect(isValidCidrInput("10.0.0.0/２４")).toBe(false);
    });

    it("should reject IPv4 tail not at address end (codex sol r2 P2-2)", () => {
      // 压缩左侧出现 v4 点分尾 = 不在整地址末位，Java/TS 双侧拒绝（对齐 JDK 数值语义）
      expect(isValidCidrInput("1.2.3.4::/64")).toBe(false);
      expect(isValidCidrInput("1.2.3.4::5:6/64")).toBe(false);
      // 末位 v4 尾保持合法（压缩右侧末组 / 无压缩第 8 组）
      expect(isValidCidrInput("::1.2.3.4")).toBe(true);
      expect(isValidCidrInput("1:2:3:4:5:6:1.2.3.4/96")).toBe(true);
    });

    it("should reject negative/over-family/signed prefixes and hostname forms", () => {
      for (const v of [
        "192.168.0.0/-1",
        "10.0.0.0/-100",
        "192.168.0.0/33",
        "2001:db8::/129",
        "10.0.0.0/+8",
        "10.0.0.0/ 8",
        "10.0.0.0/8x",
        "10.0.0.0/",
        "10.0.0.256/24",
        "evil.example.com/24",
        "not-an-ip",
        ""
      ]) {
        expect(isValidCidrInput(v)).toBe(false);
      }
    });
  });
});
