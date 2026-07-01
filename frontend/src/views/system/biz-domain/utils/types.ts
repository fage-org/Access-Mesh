import type { BizDomainResp } from "@/api/biz-domain";
import type { DomainConfigResp } from "@/api/domain-config";

/** 业务域表单数据（新建/编辑共用）。
 *  code 为唯一键（uk_biz_domain），编辑只读——改它等于新建新域，与 type-def 稳定编码同口径。 */
export interface BizDomainFormData {
  /** 业务域编码（新建必填，编辑只读——租户内唯一） */
  code: string;
  /** 业务域名称（必填） */
  name: string;
  /** 描述（可空） */
  description: string;
}

/** 域配置表单数据（新建/编辑共用，提交统一走 save upsert）。
 *  configType 为 upsert 键的一部分（domainCode+configType），编辑只读。
 *  extra 为 JSON 字符串（后端 schema 是 JSONB，前端按字符串编辑 + JSON.parse 校验）。 */
export interface DomainConfigFormData {
  /** 配置类型（新建下拉选 5 种，编辑只读——upsert 键） */
  configType: string;
  /** 配置值（JSON 字符串，必填，提交前 JSON.parse 校验合法性） */
  extra: string;
}

/** 新建业务域默认表单 */
export function createEmptyBizDomainForm(): BizDomainFormData {
  return {
    code: "",
    name: "",
    description: ""
  };
}

/** 新建域配置默认表单 */
export function createEmptyDomainConfigForm(): DomainConfigFormData {
  return {
    configType: "",
    extra: "{}"
  };
}

/**
 * 域配置类型选项（对齐 schema domain_config.config_type 注释，permission-center.sql:482-483）。
 *
 * schema 注释列 5 种：SCOPE / RELATION / BINDING / SUB_PERM / CLASSIFY。
 * 🔧 后端 AppServiceImpl.upsertDomainConfig 注释只提 CLASSIFY/SUB_PERM，schema 注释列 5 种，登记 T-PERM-026。
 * 前端下拉列全 5 种，不限于 CLASSIFY（任务标题「CLASSIFY 类型归属」实为 configType 之一）。
 */
export const CONFIG_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "CLASSIFY（域分类配置）", value: "CLASSIFY" },
  { label: "SCOPE（域范围）", value: "SCOPE" },
  { label: "RELATION（域关系）", value: "RELATION" },
  { label: "BINDING（域绑定）", value: "BINDING" },
  { label: "SUB_PERM（子权限配置）", value: "SUB_PERM" }
];

/** configType 编码 → 中文标签（用于表格 el-tag 展示，匹配不上回退原值） */
export const configTypeLabel = (code?: string | null): string => {
  if (!code) return "—";
  return CONFIG_TYPE_OPTIONS.find(o => o.value === code)?.label ?? code;
};

/** extra JSON 解析结果。ok=false 时 error 为中文错误信息。 */
export interface ParsedExtra {
  ok: boolean;
  value?: unknown;
  error?: string;
}

/** 校验 extra 是否为合法 JSON 字符串。
 *  与后端 JsonValidationUtils.validateJson 对齐——非法 JSON 提交会被后端拒绝，
 *  前端提前拦截给出中文提示。空字符串视为非法（extra @NotBlank）。 */
export function parseExtra(jsonStr: string): ParsedExtra {
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

export type { BizDomainResp, DomainConfigResp };
