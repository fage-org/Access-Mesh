import { describe, it, expect } from "vitest";
import {
  createEmptyRules,
  createEmptyItem,
  serializeRules,
  parseRules,
  summarizeRules,
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
});
