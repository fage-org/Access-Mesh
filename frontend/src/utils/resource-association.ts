export type AssociationMode = "NONE" | "EXCLUSIVE" | "SHARED" | "UNKNOWN";

/** 未声明保留普通类型；无法理解的声明不能被当作无限制类型。 */
export function resourceDeclaration(extra: string | null | undefined) {
  let root: Record<string, unknown> = {};
  try {
    const value: unknown = extra?.trim() ? JSON.parse(extra) : {};
    if (!value || typeof value !== "object" || Array.isArray(value))
      throw new Error("invalid declaration");
    root = value as Record<string, unknown>;
  } catch {
    return {
      mode: "UNKNOWN" as AssociationMode,
      managedMode: "UNKNOWN",
      source: "",
      parentTypes: [] as string[]
    };
  }
  const declared =
    root.associationMode === undefined ? "NONE" : root.associationMode;
  const mode: AssociationMode =
    declared === "NONE" || declared === "EXCLUSIVE" || declared === "SHARED"
      ? declared
      : "UNKNOWN";
  return {
    mode,
    managedMode:
      typeof root.managedMode === "string" ? root.managedMode : "MANAGED",
    source:
      typeof root.syncSourceService === "string" ? root.syncSourceService : "",
    parentTypes: Array.isArray(root.associationParentTypeCodes)
      ? root.associationParentTypeCodes.filter(
          (v): v is string => typeof v === "string"
        )
      : []
  };
}

export const associationLabel = (mode: AssociationMode) =>
  ({
    NONE: "普通资源",
    EXCLUSIVE: "专属资源",
    SHARED: "通用资源（整类引用）",
    UNKNOWN: "不支持的关系声明"
  })[mode];

/** 只写用户触达的声明字段，保留所有者指针及其他扩展资料。 */
export function updateResourceDeclaration(
  extra: string,
  key: string,
  value: unknown
): string {
  const root: unknown = extra.trim() ? JSON.parse(extra) : {};
  if (!root || typeof root !== "object" || Array.isArray(root))
    throw new Error("扩展属性必须是 JSON 对象");
  return JSON.stringify({ ...root, [key]: value });
}
