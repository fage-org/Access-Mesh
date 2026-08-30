export type MockCondition = {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  conditionRules: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
  description: string | null;
  createdAt: string;
  /** T-PERM-029：对齐后端 ConditionResp.updatedAt（此前 mock/Resp 均无该字段） */
  updatedAt: string;
  deleted: boolean;
};

const BASE_TIME = "2026-06-18 09:30:00";

export const MOCK_PERMISSION_CONDITION_STORAGE_KEY =
  "access-mesh:mock:permission-conditions:v1";

const initialMockConditions: MockCondition[] = [
  {
    id: 601,
    tenantId: 1,
    code: "office-hours",
    name: "工作时间",
    conditionRules:
      '{"logic":"AND","items":[{"type":"TIME_RANGE","params":{"start":"09:00:00","end":"18:00:00"}}]}',
    enabled: true,
    gatewayEvaluable: true,
    description: "仅工作时间段可访问",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 602,
    tenantId: 1,
    code: "corp-ip-only",
    name: "公司网络",
    conditionRules:
      '{"logic":"AND","items":[{"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24","10.0.0.0/8"]}}]}',
    enabled: true,
    gatewayEvaluable: true,
    description: "仅公司内网 IP 可访问",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 603,
    tenantId: 1,
    code: "temp-access",
    name: "临时开放窗口",
    conditionRules:
      '{"logic":"AND","items":[{"type":"DATE_RANGE","params":{"start":"2026-07-01","end":"2026-07-31"}},{"type":"TIME_RANGE","params":{"start":"09:00:00","end":"18:00:00"}}]}',
    enabled: false,
    gatewayEvaluable: true,
    description: "2026 年 7 月工作日时间窗（当前停用）",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 604,
    tenantId: 1,
    code: "blacklist-vpn",
    name: "VPN 黑名单",
    conditionRules:
      '{"logic":"AND","items":[{"type":"IP_BLACKLIST","params":{"cidrs":["203.0.113.0/24"]}}]}',
    enabled: true,
    gatewayEvaluable: false,
    description: "封禁已知 VPN 出口（走实时鉴权）",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  }
];

type SharedMockConditionState = {
  conditions: MockCondition[];
};

type AccessMeshMockGlobal = typeof globalThis & {
  __ACCESS_MESH_PERMISSION_CONDITION_MOCK_V1__?: SharedMockConditionState;
};

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

function isStoredCondition(value: unknown): value is MockCondition {
  if (typeof value !== "object" || value == null) return false;
  const item = value as Partial<MockCondition>;
  return (
    typeof item.id === "number" &&
    typeof item.tenantId === "number" &&
    typeof item.code === "string" &&
    typeof item.name === "string" &&
    typeof item.conditionRules === "string" &&
    typeof item.enabled === "boolean" &&
    typeof item.gatewayEvaluable === "boolean" &&
    (typeof item.description === "string" || item.description === null) &&
    typeof item.createdAt === "string" &&
    typeof item.updatedAt === "string" &&
    typeof item.deleted === "boolean"
  );
}

function readStoredConditions(): MockCondition[] | null {
  const target = storage();
  if (!target) return null;
  try {
    const raw = target.getItem(MOCK_PERMISSION_CONDITION_STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.every(isStoredCondition)
      ? parsed
      : null;
  } catch {
    return null;
  }
}

function cloneInitialConditions(): MockCondition[] {
  return initialMockConditions.map(condition => ({ ...condition }));
}

/**
 * fake-server 在开发环境会分别打包每个 mock 文件，同一共享模块因此可能出现多个模块实例。
 * 将状态挂到服务进程 globalThis，确保 permission-condition 与 permission-grant 路由使用同一数组。
 */
function sharedMockConditionState(): SharedMockConditionState {
  const root = globalThis as AccessMeshMockGlobal;
  root.__ACCESS_MESH_PERMISSION_CONDITION_MOCK_V1__ ??= {
    conditions: readStoredConditions() ?? cloneInitialConditions()
  };
  return root.__ACCESS_MESH_PERMISSION_CONDITION_MOCK_V1__;
}

/**
 * 权限条件 mock 的唯一状态源。
 *
 * 条件管理 CRUD 与授权计划校验必须共享本数组，避免条件启停后
 * permission-condition/list 与 apply-grant-plan 对同一 conditionCode 得出不同结论。
 */
export const mockConditions: MockCondition[] =
  sharedMockConditionState().conditions;

/**
 * 每次 mock 请求前从同源 localStorage 重载，确保不同浏览器标签页看到同一条件状态。
 * Node/Vitest 环境没有 localStorage 时保持原有进程内共享语义。
 */
export function syncMockConditionsFromStorage(): void {
  const stored = readStoredConditions();
  if (!stored) return;
  mockConditions.splice(0, mockConditions.length, ...stored);
}

/** 条件 CRUD 成功后持久化，供其他标签页的授权校验读取。 */
export function persistMockConditions(): void {
  const target = storage();
  if (!target) return;
  try {
    target.setItem(
      MOCK_PERMISSION_CONDITION_STORAGE_KEY,
      JSON.stringify(mockConditions)
    );
  } catch {
    // mock 存储不可用时退化为当前标签页内存状态，不影响接口本身。
  }
}

export function allocateMockConditionId(): number {
  syncMockConditionsFromStorage();
  return Math.max(700, ...mockConditions.map(condition => condition.id)) + 1;
}

export function findActiveMockCondition(
  code: string
): MockCondition | undefined {
  syncMockConditionsFromStorage();
  return mockConditions.find(
    condition => !condition.deleted && condition.code === code
  );
}

export function isKnownMockCondition(code: string): boolean {
  return findActiveMockCondition(code) != null;
}

export function isEnabledMockCondition(code: string): boolean {
  return findActiveMockCondition(code)?.enabled === true;
}
