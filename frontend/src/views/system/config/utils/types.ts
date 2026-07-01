import type { SystemConfigResp } from "@/api/system-config";

/** 系统配置表单数据（新建/编辑共用，提交统一走 save upsert）。
 *  configValue 为 JSON 字符串（后端 schema 是 JSONB，前端按字符串编辑 + JSON.parse 校验）。 */
export interface SystemConfigFormData {
  /** 配置键（新建必填，编辑只读——唯一键，改它等于新建新项，与 type-def 稳定编码同口径） */
  configKey: string;
  /** 配置值（JSON 字符串，必填，提交前 JSON.parse 校验合法性） */
  configValue: string;
  /** 描述（可空） */
  description: string;
}

/** 新建默认表单 */
export function createEmptyConfigForm(): SystemConfigFormData {
  return {
    configKey: "",
    configValue: "{}",
    description: ""
  };
}

/** configValue JSON 解析结果。ok=false 时 error 为中文错误信息。 */
export interface ParsedConfigValue {
  ok: boolean;
  value?: unknown;
  error?: string;
}

/** 校验 configValue 是否为合法 JSON 字符串。
 *  与后端 JsonValidationUtils.validateJson 对齐——非法 JSON 提交会被后端拒绝，
 *  前端提前拦截给出中文提示。空字符串视为非法（configValue @NotBlank）。 */
export function parseConfigValue(jsonStr: string): ParsedConfigValue {
  const trimmed = jsonStr.trim();
  if (!trimmed) {
    return { ok: false, error: "配置值不能为空" };
  }
  try {
    const value = JSON.parse(trimmed);
    return { ok: true, value };
  } catch {
    return { ok: false, error: "配置值必须是合法 JSON" };
  }
}

export type { SystemConfigResp };
