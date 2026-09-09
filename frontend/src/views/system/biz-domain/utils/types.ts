import type { BizDomainResp } from "@/api/biz-domain";
import type { DomainConfigResp } from "@/api/domain-config";

/** 业务域表单数据（新建/编辑共用）。
 *  code 为唯一键（uk_biz_domain），编辑只读——改它等于新建新域，与 type-def 稳定编码同口径。
 *  global 仅 create 态生效（T-PERM-046：true=全局域每租户仅一个；创建后不可变，edit 表单不展示开关）。 */
export interface BizDomainFormData {
  /** 业务域编码（新建必填，编辑只读——租户内唯一） */
  code: string;
  /** 业务域名称（必填） */
  name: string;
  /** 描述（可空） */
  description: string;
  /** 是否全局域（仅新建提交携带；编辑态恒 false 且不提交） */
  global: boolean;
}

/** 域配置表单数据（新建/编辑共用，提交统一走 save upsert）。
 *  configType 为 upsert 键的一部分（domainCode+configType），编辑只读。
 *  extra 为 JSON 字符串（后端 schema 是 JSONB，前端按字符串编辑 + JSON.parse 校验）。 */
export interface DomainConfigFormData {
  /** 配置类型（新建下拉选 SUB_PERM/CLASSIFY 两类，编辑只读——upsert 键；写入白名单拒绝其余历史类型） */
  configType: string;
  /** 配置值（JSON 字符串，必填，提交前 JSON.parse 校验合法性） */
  extra: string;
}

/** 新建业务域默认表单 */
export function createEmptyBizDomainForm(): BizDomainFormData {
  return {
    code: "",
    name: "",
    description: "",
    global: false
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
 * 域配置类型选项（对齐 schema domain_config.config_type 注释与后端 ConfigType 枚举）。
 *
 * 仅 SUB_PERM / CLASSIFY 两类已实现并接受写入（后端 DomainConfigReq 白名单校验拒绝其余值）；
 * SCOPE / RELATION / BINDING 为历史设想类型，未实现，不再提供选项（原 5 种下拉已于 2026-08-27 收窄）。
 */
export const CONFIG_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "CLASSIFY（域分类配置）", value: "CLASSIFY" },
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
