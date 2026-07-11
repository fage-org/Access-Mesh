import type {
  ApiMappingResp,
  ServiceConfigResp,
  SyncApiGroup
} from "@/api/service-interface";

export type HttpMethod =
  | "GET"
  | "POST"
  | "PUT"
  | "DELETE"
  | "PATCH"
  | "HEAD"
  | "OPTIONS";

export const HTTP_METHOD_OPTIONS: ReadonlyArray<{
  label: HttpMethod;
  value: HttpMethod;
}> = [
  { label: "GET", value: "GET" },
  { label: "POST", value: "POST" },
  { label: "PUT", value: "PUT" },
  { label: "DELETE", value: "DELETE" },
  { label: "PATCH", value: "PATCH" },
  { label: "HEAD", value: "HEAD" },
  { label: "OPTIONS", value: "OPTIONS" }
];

export function methodTagType(
  method: string
): "success" | "primary" | "warning" | "danger" | "info" {
  const types: Record<
    string,
    "success" | "primary" | "warning" | "danger" | "info"
  > = {
    GET: "success",
    POST: "primary",
    PUT: "warning",
    DELETE: "danger",
    PATCH: "warning",
    HEAD: "info",
    OPTIONS: "info"
  };
  return types[method.toUpperCase()] ?? "info";
}

export interface ServiceConfigFormData {
  serviceCode: string;
  name: string;
  basePath: string;
  description: string;
  status: number;
  extra: string;
}

export interface MappingFormData {
  resourceEntityId: number | null;
  httpMethod: HttpMethod;
  pathPattern: string;
  matchOrder: number;
  enabled: boolean;
  extra: string;
}

export interface SyncFormData {
  basePath: string;
  groupsJson: string;
}

export interface ServiceSummary extends ServiceConfigResp {
  apiCount: number;
  enabledApiCount: number;
}

export const createEmptyServiceForm = (): ServiceConfigFormData => ({
  serviceCode: "",
  name: "",
  basePath: "/",
  description: "",
  status: 1,
  extra: ""
});

export const createEmptyMappingForm = (): MappingFormData => ({
  resourceEntityId: null,
  httpMethod: "POST",
  pathPattern: "/api/",
  matchOrder: 100,
  enabled: true,
  extra: ""
});

export function createSyncPayload(service: ServiceConfigResp): SyncFormData {
  const sampleGroups: SyncApiGroup[] = [
    {
      groupCode: "management",
      groupName: "管理接口",
      apis: [
        {
          name: "查询列表",
          httpMethod: "POST",
          path: "/api/example/list",
          operationCode: "VIEW",
          resourceCode: `${service.serviceCode}:example:list`,
          description: "请用服务实际暴露的接口清单替换此示例"
        }
      ]
    }
  ];
  return {
    basePath: service.basePath || "/",
    groupsJson: JSON.stringify(sampleGroups, null, 2)
  };
}

/** 可选 extra 字段按 JSON 字符串传递；空值表示不提交。 */
export function validateOptionalJson(raw: string): string | null {
  const value = raw.trim();
  if (!value) return null;
  try {
    JSON.parse(value);
    return null;
  } catch {
    return "扩展属性必须是合法 JSON";
  }
}

export function parseSyncGroups(raw: string): {
  groups?: SyncApiGroup[];
  error?: string;
} {
  try {
    const value: unknown = JSON.parse(raw);
    if (!Array.isArray(value) || value.length === 0) {
      return { error: "请至少提供一个接口分组" };
    }
    for (const group of value) {
      if (
        !group ||
        typeof group !== "object" ||
        !("groupCode" in group) ||
        !("groupName" in group) ||
        !("apis" in group) ||
        typeof group.groupCode !== "string" ||
        typeof group.groupName !== "string" ||
        !Array.isArray(group.apis) ||
        !group.groupCode.trim() ||
        !group.groupName.trim()
      ) {
        return { error: "每个分组必须包含 groupCode、groupName 和 apis" };
      }
      for (const api of group.apis) {
        if (
          !api ||
          typeof api !== "object" ||
          !("name" in api) ||
          !("httpMethod" in api) ||
          !("path" in api) ||
          !("operationCode" in api) ||
          !("resourceCode" in api) ||
          typeof api.name !== "string" ||
          typeof api.httpMethod !== "string" ||
          typeof api.path !== "string" ||
          typeof api.operationCode !== "string" ||
          typeof api.resourceCode !== "string" ||
          !api.name.trim() ||
          !api.path.startsWith("/") ||
          !api.operationCode.trim() ||
          !api.resourceCode.trim()
        ) {
          return {
            error:
              "每个接口必须包含 name、httpMethod、以 / 开头的 path、operationCode 和 resourceCode"
          };
        }
      }
    }
    return { groups: value as SyncApiGroup[] };
  } catch {
    return { error: "接口清单必须是合法 JSON" };
  }
}

export function mappingToForm(row: ApiMappingResp): MappingFormData {
  const method = HTTP_METHOD_OPTIONS.some(item => item.value === row.httpMethod)
    ? (row.httpMethod as HttpMethod)
    : "POST";
  return {
    resourceEntityId: row.resourceEntityId,
    httpMethod: method,
    pathPattern: row.pathPattern,
    matchOrder: row.matchOrder ?? 100,
    enabled: row.enabled,
    extra: row.extra || ""
  };
}
