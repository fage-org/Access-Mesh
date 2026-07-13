/**
 * 权限条件页表单类型。
 *
 * 规则模型（ConditionRules / ConditionItem / 序列化 / 摘要 / 常量）已抽取到
 * `@/utils/condition-rules`（页面无关共享模块），此处 re-export 保持向后兼容；
 * 本文件仅保留条件表单元信息类型 ConditionFormData。
 *
 * 字段对齐后端 DTO（api-contract.md §5.6）：
 * - ConditionCreateReq / ConditionUpdateReq
 * - conditionRules 结构对齐 ConditionEvalUtils（perm-common）：
 *   {logic: "AND"|"OR", items: [{type, params}]}
 * - 预置类型：DATE_RANGE / TIME_RANGE / IP_WHITELIST / IP_BLACKLIST
 *   （对齐 PermConstants.ConditionType + ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES）
 */

// 规则模型 re-export（向后兼容，实际定义见 @/utils/condition-rules）
export {
  CONDITION_LOGIC_OPTIONS,
  CONDITION_TYPE_OPTIONS,
  CONDITION_TYPE_LABEL,
  createEmptyRules,
  createEmptyItem,
  serializeRules,
  parseRules,
  summarizeRules,
  type ConditionItem,
  type ConditionRules
} from "@/utils/condition-rules";

import { createEmptyRules, type ConditionRules } from "@/utils/condition-rules";

/** 条件新增/编辑表单数据 */
export type ConditionFormData = {
  code: string;
  name: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
  description: string;
  /** 结构化规则（提交时序列化为 JSON 字符串） */
  rules: ConditionRules;
};

/** 条件表单空值工厂（新建用） */
export function createEmptyConditionForm(): ConditionFormData {
  return {
    code: "",
    name: "",
    enabled: true,
    gatewayEvaluable: false,
    description: "",
    rules: createEmptyRules()
  };
}
