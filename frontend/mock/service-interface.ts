// 服务注册与接口映射 Mock（T-FE-007）。
// 对齐 permission-center 的 service-config / resource-api-mapping 接口；
// FULL 同步严格只清理由服务自动维护的映射，手工映射会被保留。
import { defineFakeRoute } from "vite-plugin-fake-server/client";

type ServiceConfigResp = {
  id: number;
  tenantId: number;
  serviceCode: string;
  name: string;
  basePath: string;
  description: string | null;
  status: number;
  extra: string | null;
  createdAt: string;
};

type ApiMappingResp = {
  id: number;
  tenantId: number;
  resourceEntityId: number;
  serviceCode: string;
  httpMethod: string;
  pathPattern: string;
  matchOrder: number;
  enabled: boolean;
  extra: string | null;
  createdAt: string;
  updatedAt: string;
};

type InternalMapping = ApiMappingResp & {
  source: "SERVICE_SYNC" | "MANUAL";
  resourceCode: string;
};

type SyncApiItem = {
  name?: string;
  httpMethod?: string;
  path?: string;
  operationCode?: string;
  resourceCode?: string;
  description?: string | null;
};

type SyncGroup = {
  groupCode?: string;
  groupName?: string;
  apis?: SyncApiItem[];
};

const now = () => new Date().toISOString().slice(0, 19).replace("T", " ");
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

let nextServiceId = 104;
let nextMappingId = 310;
let nextResourceId = 1020;

const services: ServiceConfigResp[] = [
  {
    id: 101,
    tenantId: 1,
    serviceCode: "admin-service",
    name: "管理服务",
    basePath: "/admin",
    description: "组织、用户、菜单与认证管理",
    status: 1,
    extra: '{"owner":"identity"}',
    createdAt: "2026-06-18 09:30:00"
  },
  {
    id: 102,
    tenantId: 1,
    serviceCode: "permission-center",
    name: "权限中心",
    basePath: "/perm",
    description: "权限事实、授权关系与运行时校验",
    status: 1,
    extra: '{"owner":"security"}',
    createdAt: "2026-06-18 09:35:00"
  },
  {
    id: 103,
    tenantId: 1,
    serviceCode: "example-service",
    name: "示例服务",
    basePath: "/example",
    description: "接入方接口与范围权限演示",
    status: 0,
    extra: null,
    createdAt: "2026-06-18 09:40:00"
  }
];

let mappings: InternalMapping[] = [
  {
    id: 301,
    tenantId: 1,
    resourceEntityId: 1001,
    serviceCode: "admin-service",
    httpMethod: "POST",
    pathPattern: "/admin/api/user/list",
    matchOrder: 30,
    enabled: true,
    extra: null,
    createdAt: "2026-06-21 11:20:00",
    updatedAt: "2026-06-21 11:20:00",
    source: "SERVICE_SYNC",
    resourceCode: "admin:user:list"
  },
  {
    id: 302,
    tenantId: 1,
    resourceEntityId: 1002,
    serviceCode: "admin-service",
    httpMethod: "POST",
    pathPattern: "/admin/api/org/tree",
    matchOrder: 30,
    enabled: true,
    extra: null,
    createdAt: "2026-06-21 11:20:00",
    updatedAt: "2026-06-21 11:20:00",
    source: "SERVICE_SYNC",
    resourceCode: "admin:org:tree"
  },
  {
    id: 303,
    tenantId: 1,
    resourceEntityId: 1003,
    serviceCode: "permission-center",
    httpMethod: "POST",
    pathPattern: "/perm/api/perm/auth/check",
    matchOrder: 10,
    enabled: true,
    extra: '{"gatewayOnly":true}',
    createdAt: "2026-06-23 16:10:00",
    updatedAt: "2026-06-23 16:10:00",
    source: "SERVICE_SYNC",
    resourceCode: "perm:auth:check"
  },
  {
    id: 304,
    tenantId: 1,
    resourceEntityId: 1004,
    serviceCode: "permission-center",
    httpMethod: "POST",
    pathPattern: "/perm/api/perm/permission-view/explain",
    matchOrder: 80,
    enabled: false,
    extra: null,
    createdAt: "2026-06-23 16:12:00",
    updatedAt: "2026-06-28 10:00:00",
    source: "MANUAL",
    resourceCode: "perm:permission:explain"
  },
  {
    id: 305,
    tenantId: 1,
    resourceEntityId: 1005,
    serviceCode: "example-service",
    httpMethod: "POST",
    pathPattern: "/example/api/report/list",
    matchOrder: 50,
    enabled: true,
    extra: null,
    createdAt: "2026-06-24 14:00:00",
    updatedAt: "2026-06-24 14:00:00",
    source: "SERVICE_SYNC",
    resourceCode: "example:report:list"
  }
];

function cloneService(service: ServiceConfigResp): ServiceConfigResp {
  return { ...service };
}

function cloneMapping(mapping: InternalMapping): ApiMappingResp {
  const { source: _source, resourceCode: _resourceCode, ...response } = mapping;
  return { ...response };
}

function findService(serviceCode: unknown): ServiceConfigResp | undefined {
  return services.find(service => service.serviceCode === serviceCode);
}

function joinPath(basePath: string, path: string): string {
  const base = basePath === "/" ? "" : basePath.replace(/\/$/, "");
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${base}${suffix}` || "/";
}

function isValidSyncGroup(group: SyncGroup): boolean {
  return (
    !!group.groupCode &&
    !!group.groupName &&
    Array.isArray(group.apis) &&
    group.apis.every(
      api =>
        !!api.name &&
        !!api.httpMethod &&
        !!api.path?.startsWith("/") &&
        !!api.operationCode &&
        !!api.resourceCode
    )
  );
}

export default defineFakeRoute([
  {
    url: "/api/perm/service-config/list",
    method: "post",
    response: () => ok({ items: services.map(cloneService) })
  },
  {
    url: "/api/perm/service-config/detail",
    method: "post",
    response: ({ body }) => {
      const service = findService(body?.serviceCode);
      return service ? ok(cloneService(service)) : error(404, "服务不存在");
    }
  },
  {
    url: "/api/perm/service-config/save",
    method: "post",
    response: ({ body }) => {
      const { serviceCode, name, basePath, description, status, extra } =
        body || {};
      if (!serviceCode || !name)
        return error(400, "服务编码和服务名称不能为空");
      if (basePath != null && !String(basePath).startsWith("/")) {
        return error(400, "基础路径必须以 / 开头");
      }
      const existed = findService(serviceCode);
      if (existed) {
        existed.name = name;
        existed.basePath = basePath || "/";
        existed.description = description ?? null;
        existed.status = status ?? existed.status;
        existed.extra = extra ?? null;
        return ok(cloneService(existed));
      }
      const created: ServiceConfigResp = {
        id: nextServiceId++,
        tenantId: 1,
        serviceCode,
        name,
        basePath: basePath || "/",
        description: description ?? null,
        status: status ?? 1,
        extra: extra ?? null,
        createdAt: now()
      };
      services.push(created);
      return ok(cloneService(created));
    }
  },
  {
    url: "/api/perm/service-config/remove",
    method: "post",
    response: ({ body }) => {
      const ids = body?.ids;
      if (!Array.isArray(ids)) return error(400, "ids 必须是数组");
      const serviceCodes = new Set(
        services
          .filter(service => ids.includes(service.id))
          .map(service => service.serviceCode)
      );
      for (let index = services.length - 1; index >= 0; index -= 1) {
        if (ids.includes(services[index].id)) services.splice(index, 1);
      }
      mappings = mappings.filter(
        mapping => !serviceCodes.has(mapping.serviceCode)
      );
      return ok(null);
    }
  },
  {
    url: "/api/perm/service-config/apis",
    method: "post",
    response: ({ body }) => {
      const serviceCode = body?.serviceCode;
      if (!findService(serviceCode)) return error(404, "服务不存在");
      return ok({
        items: mappings
          .filter(mapping => mapping.serviceCode === serviceCode)
          .map(cloneMapping)
      });
    }
  },
  {
    url: "/api/perm/resource-api-mapping/list",
    method: "post",
    response: ({ body }) => {
      const { resourceId, serviceCode } = body || {};
      return ok({
        items: mappings
          .filter(
            mapping =>
              resourceId == null || mapping.resourceEntityId === resourceId
          )
          .filter(
            mapping => !serviceCode || mapping.serviceCode === serviceCode
          )
          .map(cloneMapping)
      });
    }
  },
  {
    url: "/api/perm/resource-api-mapping/create",
    method: "post",
    response: ({ body }) => {
      const {
        resourceId,
        serviceCode,
        httpMethod,
        pathPattern,
        matchOrder,
        enabled,
        extra
      } = body || {};
      if (!findService(serviceCode)) return error(404, "服务不存在");
      if (!Number.isInteger(resourceId) || resourceId <= 0) {
        return error(400, "resourceId 必须是正整数");
      }
      if (!httpMethod || !pathPattern?.startsWith("/")) {
        return error(400, "HTTP 方法和以 / 开头的路径模式不能为空");
      }
      if (
        mappings.some(
          mapping =>
            mapping.resourceEntityId === resourceId &&
            mapping.serviceCode === serviceCode &&
            mapping.httpMethod === httpMethod &&
            mapping.pathPattern === pathPattern
        )
      ) {
        return error(409, "该资源与接口的映射已存在");
      }
      const createdAt = now();
      const created: InternalMapping = {
        id: nextMappingId++,
        tenantId: 1,
        resourceEntityId: resourceId,
        serviceCode,
        httpMethod,
        pathPattern,
        matchOrder: matchOrder ?? 100,
        enabled: enabled ?? true,
        extra: extra ?? null,
        createdAt,
        updatedAt: createdAt,
        source: "MANUAL",
        resourceCode: `manual:resource:${resourceId}`
      };
      mappings.push(created);
      return ok(cloneMapping(created));
    }
  },
  {
    url: "/api/perm/resource-api-mapping/update",
    method: "post",
    response: ({ body }) => {
      const {
        resourceId,
        mappingId,
        httpMethod,
        pathPattern,
        matchOrder,
        enabled,
        extra
      } = body || {};
      const mapping = mappings.find(item => item.id === mappingId);
      if (!mapping || mapping.resourceEntityId !== resourceId) {
        return error(404, "API 映射不存在");
      }
      if (pathPattern != null && !String(pathPattern).startsWith("/")) {
        return error(400, "路径模式必须以 / 开头");
      }
      if (httpMethod != null) mapping.httpMethod = httpMethod;
      if (pathPattern != null) mapping.pathPattern = pathPattern;
      if (matchOrder != null) mapping.matchOrder = matchOrder;
      if (enabled != null) mapping.enabled = enabled;
      if (extra != null) mapping.extra = extra;
      mapping.updatedAt = now();
      return ok(cloneMapping(mapping));
    }
  },
  {
    url: "/api/perm/resource-api-mapping/remove",
    method: "post",
    response: ({ body }) => {
      const ids = body?.ids;
      if (!Array.isArray(ids)) return error(400, "ids 必须是数组");
      mappings = mappings.filter(mapping => !ids.includes(mapping.id));
      return ok(null);
    }
  },
  {
    url: "/api/perm/service-config/sync",
    method: "post",
    response: ({ body }) => {
      const { serviceCode, basePath, syncMode, groups } = body || {};
      const service = findService(serviceCode);
      if (!service) return error(404, "服务不存在");
      if (syncMode !== "FULL") return error(400, "当前仅支持 FULL 同步");
      if (
        !Array.isArray(groups) ||
        groups.length === 0 ||
        !groups.every(isValidSyncGroup)
      ) {
        return error(400, "接口分组清单不完整");
      }
      const resolvedBasePath = basePath || service.basePath || "/";
      service.basePath = resolvedBasePath;

      const incomingKeys = new Set<string>();
      let createdResources = 0;
      let createdMappings = 0;
      let updatedMappings = 0;
      for (const group of groups as SyncGroup[]) {
        for (const api of group.apis || []) {
          const method = String(api.httpMethod).toUpperCase();
          const fullPath = joinPath(resolvedBasePath, String(api.path));
          const resourceCode = String(api.resourceCode);
          const key = `${method}|${fullPath}|${resourceCode}`;
          incomingKeys.add(key);
          const existed = mappings.find(
            mapping =>
              mapping.serviceCode === serviceCode &&
              mapping.httpMethod === method &&
              mapping.pathPattern === fullPath &&
              mapping.resourceCode === resourceCode
          );
          if (existed) {
            existed.enabled = true;
            existed.updatedAt = now();
            updatedMappings += 1;
            continue;
          }
          const resourceEntityId = nextResourceId++;
          const createdAt = now();
          mappings.push({
            id: nextMappingId++,
            tenantId: 1,
            resourceEntityId,
            serviceCode,
            httpMethod: method,
            pathPattern: fullPath,
            matchOrder: 100,
            enabled: true,
            extra: api.description
              ? JSON.stringify({ description: api.description })
              : null,
            createdAt,
            updatedAt: createdAt,
            source: "SERVICE_SYNC",
            resourceCode
          });
          createdResources += 1;
          createdMappings += 1;
        }
      }

      let deletedMappings = 0;
      mappings = mappings.filter(mapping => {
        if (
          mapping.serviceCode !== serviceCode ||
          mapping.source !== "SERVICE_SYNC"
        ) {
          return true;
        }
        const key = `${mapping.httpMethod}|${mapping.pathPattern}|${mapping.resourceCode}`;
        const keep = incomingKeys.has(key);
        if (!keep) deletedMappings += 1;
        return keep;
      });
      return ok({
        createdResources,
        updatedResources: 0,
        createdMappings,
        updatedMappings,
        deletedResources: deletedMappings,
        deletedMappings
      });
    }
  }
]);
