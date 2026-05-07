/**
 * 通用常量定义
 */

// ========== 状态常量 ==========

/** 启用状态 */
export const STATUS_ENABLED = 1;

/** 停用状态 */
export const STATUS_DISABLED = 0;

/** 状态标签映射 */
export const STATUS_TAGS = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
} as const;

export const getStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === STATUS_ENABLED ? STATUS_TAGS.ENABLED : STATUS_TAGS.DISABLED;
};
