export type MockSubPermissionConfig = {
  exists: boolean;
  extra: string | null;
};

export type MockSubPermissionMode = "ALLOW_ALL" | "ALLOW_LIST" | "ALLOW_NONE";

export type MockSubPermissionPolicy = {
  mode: MockSubPermissionMode;
  reason: string | null;
  allowedChildResourceTypeCodes: string[];
};

type ParsedItem = {
  parent_type: string;
  child_types: string[];
};

const deny = (reason: string): MockSubPermissionPolicy => ({
  mode: "ALLOW_NONE",
  reason,
  allowedChildResourceTypeCodes: []
});

/**
 * 解析单个业务域的 SUB_PERM 配置。
 * 判定顺序严格对齐 api-contract §6.5.2 的步骤 0~6。
 */
export function parseMockSubPermissionPolicy(
  config: MockSubPermissionConfig | undefined,
  parentResourceTypeCode: string
): MockSubPermissionPolicy {
  if (!config?.exists) return deny("CONFIG_MISSING");

  const trimmed = config.extra?.trim() ?? "";
  if (trimmed === "") return deny("CONFIG_EMPTY");
  if (trimmed === "*") {
    return {
      mode: "ALLOW_ALL",
      reason: null,
      allowedChildResourceTypeCodes: []
    };
  }

  let items: ParsedItem[];
  try {
    items = JSON.parse(trimmed);
  } catch {
    return deny("CONFIG_INVALID");
  }

  if (
    !Array.isArray(items) ||
    items.some(
      item =>
        typeof item?.parent_type !== "string" ||
        item.parent_type.trim() === "" ||
        !Array.isArray(item?.child_types) ||
        item.child_types.some(code => typeof code !== "string")
    )
  ) {
    return deny("CONFIG_INVALID");
  }

  const parentCode = parentResourceTypeCode.toLowerCase();
  const matched = items.filter(
    item => item.parent_type.toLowerCase() === parentCode
  );
  if (matched.length === 0) return deny("PARENT_NOT_CONFIGURED");

  if (matched.some(item => item.child_types.includes("*"))) {
    return {
      mode: "ALLOW_ALL",
      reason: null,
      allowedChildResourceTypeCodes: []
    };
  }

  // 子类型按大小写不敏感去重，但响应保留配置中首次出现的原文。
  const allowedChildResourceTypeCodes: string[] = [];
  const seen = new Set<string>();
  for (const code of matched.flatMap(item => item.child_types)) {
    const normalized = code.toLowerCase();
    if (seen.has(normalized)) continue;
    seen.add(normalized);
    allowedChildResourceTypeCodes.push(code);
  }

  if (allowedChildResourceTypeCodes.length === 0) {
    return deny("CHILD_TYPES_EMPTY");
  }
  return {
    mode: "ALLOW_LIST",
    reason: null,
    allowedChildResourceTypeCodes
  };
}

export function mockSubPermissionAllows(
  policy: MockSubPermissionPolicy,
  childResourceTypeCode: string
): boolean {
  if (policy.mode === "ALLOW_ALL") return true;
  if (policy.mode === "ALLOW_NONE") return false;
  return policy.allowedChildResourceTypeCodes.some(
    code => code.toLowerCase() === childResourceTypeCode.toLowerCase()
  );
}
