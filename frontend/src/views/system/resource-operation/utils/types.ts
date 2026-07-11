/**
 * 资源与操作定义页表单类型与常量。
 *
 * 字段对齐后端 DTO（api-contract.md §5.3）：
 * - 资源：ResourceCreateReq / ResourceUpdateReq / ResourceMoveReq
 * - 操作：OperationCreateReq / OperationUpdateReq
 */

/** 默认编码类型（schema resource_entity.code_type 默认 'default'） */
export const CODE_TYPE_DEFAULT = "default";

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
  sortOrder: number;
  extra: string;
};

/** 操作权限新增/编辑表单数据 */
export type OperationFormData = {
  resourceTypeCode: string;
  code: string;
  name: string;
  binaryBit: number;
  inheritMask: number;
};

/** 资源移动表单数据 */
export type ResourceMoveFormData = {
  resourceId: number;
  parentId: number | null;
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
    sortOrder: 0,
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

/**
 * 判断 n 是否为 2 的幂次（BigInt 实现，兼容 63 位 bigint 列）。
 *
 * JS `&`/`|` 强转 32 位有符号整数，>2^31 会误判
 * （如 4294967297 = 2^32+1 截断为 1，`1 & 0 === 0` 被误判为幂次）。
 * 故用 BigInt 做位与。Number 精确整数上限 2^53，覆盖全部实际业务场景。
 */
export function isPowerOfTwo(n: number): boolean {
  if (!Number.isInteger(n) || n <= 0) return false;
  const bn = BigInt(n);
  return (bn & (bn - 1n)) === 0n;
}

/**
 * 位或（BigInt 实现，兼容 63 位）。
 *
 * JS `|` 强转 32 位，>2^31 截断（如 4294967296 | 0 === 0）。
 * 返回 Number（≤2^53 精确）。
 */
export function bitOr(a: number, b: number): number {
  return Number(BigInt(a) | BigInt(b));
}
