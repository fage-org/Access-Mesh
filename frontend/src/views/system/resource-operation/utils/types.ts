/**
 * 资源与操作定义页表单类型与常量。
 *
 * 字段对齐后端 DTO（契约总册 access-service-api-contract.md §12.1）：
 * - 资源：ResourceCreateReq / ResourceUpdateReq / ResourceMoveReq
 * - 操作：OperationCreateReq / OperationUpdateReq
 */

/** 默认编码类型（schema resource_entity.code_type 默认 'default'） */
export const CODE_TYPE_DEFAULT = "default";

const MAX_OPERATION_BIT = 1n << 62n;

/** 按当前类型完整操作列表找空闲独占位；建议值不预占，并发由后端唯一约束兜底。 */
export function nextAvailableOperationBit(usedBits: string[]): string | null {
  const occupied = new Set(usedBits.map(value => BigInt(value).toString()));
  for (let bit = 1n; bit <= MAX_OPERATION_BIT; bit <<= 1n) {
    if (!occupied.has(bit.toString())) return bit.toString();
  }
  return null;
}

/** 数值控件只接受安全整数，高位使用精确十进制字符串。 */
export function operationBitError(
  value: number | string,
  usedBits: string[]
): string | null {
  if (typeof value === "number" && !Number.isSafeInteger(value)) {
    return "必须为安全整数，高位请使用精确十进制字符串";
  }
  if (!/^[0-9]+$/.test(String(value))) return "必须为正整数";
  const bit = BigInt(value);
  if (bit <= 0n || bit > MAX_OPERATION_BIT || (bit & (bit - 1n)) !== 0n) {
    return "必须为 2 的幂次（1、2、4…2^62）";
  }
  if (usedBits.some(used => BigInt(used) === bit))
    return "该独占位已被当前类型的其他操作占用";
  return null;
}

/** 资源状态选项（status：0=停用，1=启用） */
export const RESOURCE_STATUS_OPTIONS = [
  { label: "启用", value: 1 },
  { label: "停用", value: 0 }
] as const;

/** 操作权限 binaryBit 预置示例（schema 注释：CREATE(1,0) VIEW(2,0) UPDATE(4,2) DELETE(8,2)） */
export const PRESET_OPERATION_EXAMPLES = [
  { code: "CREATE", name: "创建", binaryBit: 1, inheritMask: 0 },
  { code: "VIEW", name: "查看", binaryBit: 2, inheritMask: 0 },
  { code: "UPDATE", name: "更新", binaryBit: 4, inheritMask: 2 },
  { code: "DELETE", name: "删除", binaryBit: 8, inheritMask: 2 }
] as const;

/** 资源新增/编辑表单数据 */
export type ResourceFormData = {
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
  parentId: number | null;
  status: number;
  extra: string;
};

/** 操作权限新增/编辑表单数据。
 *  位字段 number | string：安全值（≤2^53）用数值控件编辑；超精度高位值保留原始
 *  十进制字符串只读展示（Number 往返丢精度，T-PERM-028 复评 P1 收口）。 */
export type OperationFormData = {
  resourceTypeCode: string;
  code: string;
  name: string;
  binaryBit: number | string;
  inheritMask: number | string;
};

/** 资源移动表单数据（即 ResourceMoveReq 请求体：业务键对定位，parent null=顶层） */
export type ResourceMoveFormData = {
  resource: {
    resourceTypeCode: string;
    code: string;
    codeType?: string | null;
  };
  parent: {
    resourceTypeCode: string;
    code: string;
    codeType?: string | null;
  } | null;
};

/** 资源表单空值工厂（新建用） */
export function createEmptyResourceForm(
  resourceTypeCode = "",
  parentId: number | null = null
): ResourceFormData {
  return {
    resourceTypeCode,
    code: "",
    codeType: CODE_TYPE_DEFAULT,
    name: "",
    parentId,
    status: 1,
    extra: ""
  };
}

/** 操作表单空值工厂（新建用） */
export function createEmptyOperationForm(
  resourceTypeCode = ""
): OperationFormData {
  return {
    resourceTypeCode,
    code: "",
    name: "",
    binaryBit: 0,
    inheritMask: 0
  };
}

// BigInt 位运算工具已迁移至 @/utils/bit-ops（T-FE-011 抽取统一工具，兼容 63 位 bigint 列）。
// 此处 re-export 保持现有导入兼容。
export { isPowerOfTwo, bitOr, hasBit, bitsUnion } from "@/utils/bit-ops";
