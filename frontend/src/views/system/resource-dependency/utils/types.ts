/**
 * 资源依赖页表单类型与常量。
 *
 * 字段对齐后端 DTO（api-contract.md §5.6）：
 * - ResourceDependencyCreateReq / ResourceDependencyUpdateReq（全量替换）/ ResourceDependencyCheckReq
 * - 资源用业务键标识：resourceTypeCode + code + codeType
 * - 前端表单以 resourceEntityId 选择（下拉），提交时由 hook 反查业务键构造请求
 *
 * maintainSource 枚举对齐 schema（ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC），
 * 🔧 DTO 注释（SERVICE/MANUAL）与 schema 不一致，登记 T-PERM-031。
 */

/** 资源依赖新增/编辑表单数据。
 *  资源对在编辑模式下可改（全量替换契约，对齐 conflict-rule 范式，Q3=B）。 */
export type DependencyFormData = {
  /** 源资源类型编码（联动资源下拉） */
  sourceResourceTypeCode: string | null;
  /** 源资源实体 ID（下拉选择，提交时反查业务键） */
  sourceResourceEntityId: number | null;
  /** 目标资源类型编码 */
  targetResourceTypeCode: string | null;
  /** 目标资源实体 ID */
  targetResourceEntityId: number | null;
  /** 触发操作码列表（空=任意操作触发，对应 sourceOperationBits=null） */
  sourceOperationCodes: string[];
  /** 要求操作码列表（必填，对应 requiredOperationBits） */
  requiredOperationCodes: string[];
  /** 是否自动授权（默认 true） */
  autoGrant: boolean;
  description: string;
};

/** 依赖表单空值工厂（新建用） */
export function createEmptyDependencyForm(): DependencyFormData {
  return {
    sourceResourceTypeCode: null,
    sourceResourceEntityId: null,
    targetResourceTypeCode: null,
    targetResourceEntityId: null,
    sourceOperationCodes: [],
    requiredOperationCodes: [],
    autoGrant: true,
    description: ""
  };
}

/** maintainSource 枚举（对齐 schema 4 种值）。
 *  用于批量同步对话框（P0 标 TODO，常量预置供后续使用）。 */
export const MAINTAIN_SOURCE_OPTIONS = [
  { label: "管理端维护", value: "ADMIN_UI" },
  { label: "SDK 扫描", value: "SDK_SCAN" },
  { label: "声明式清单", value: "MANIFEST" },
  { label: "服务同步", value: "SERVICE_SYNC" }
] as const;

/** syncMode 枚举（批量同步用，P0 标 TODO）。 */
export const SYNC_MODE_OPTIONS = [
  { label: "全量同步", value: "FULL" },
  { label: "增量同步", value: "PARTIAL" }
] as const;
