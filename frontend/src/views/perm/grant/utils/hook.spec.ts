/**
 * 授权页类型候选降级判定回归（T-FE-018 评审收口）：
 * 前端 hasPerms 探查匹配登录权限串全集（实例级 TYPE_DEFINITION:VIEW 也产出该串），
 * 而后端 type-definition/list 门禁为类型级——仅实例级授权的账号探查通过但请求被 403 拒。
 * 旧实现落入「暂无资源类型配置」空态并弹通用错误（丢重试入口）；2026-09-02 用户决策：
 * 类型候选请求的权限拒绝同样进入降级块。资源树/操作列 403 维持通用错误（无前端前置为既定口径）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";

const getTypeDefList = vi.fn();
const getResourceTree = vi.fn();
const getOperationList = vi.fn();
const getConditionList = vi.fn();
const hasPermsMock = vi.fn();
const messageMock = vi.fn();

// 阻断 hook 模块级的 store/router/element-plus 链（同 biz-domain hook.spec 范式）
vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({}),
  onBeforeRouteLeave: () => {}
}));
vi.mock("element-plus", () => ({ ElMessageBox: { confirm: vi.fn() } }));
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));
vi.mock("@/utils/auth", () => ({
  hasPerms: (...args: unknown[]) => hasPermsMock(...args)
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => messageMock(...args)
}));
vi.mock("@/api/type-def", () => ({
  TYPE_KEY: { RESOURCE_TYPE: "resource_type" },
  getTypeDefList: (...args: unknown[]) => getTypeDefList(...args)
}));
vi.mock("@/api/resource-operation", () => ({
  getResourceTree: (...args: unknown[]) => getResourceTree(...args),
  getOperationList: (...args: unknown[]) => getOperationList(...args)
}));
vi.mock("@/api/permission-condition", () => ({
  getConditionList: (...args: unknown[]) => getConditionList(...args)
}));
vi.mock("@/api/permission-grant", () => ({
  getRolePermissionList: vi.fn()
}));

import { usePermissionGrant } from "./hook";

/** SecurityException 经 GlobalExceptionHandler 映射：HTTP 403 + PermResult code=403 */
function axios403() {
  return Object.assign(new Error("Request failed with status code 403"), {
    response: { status: 403, data: { code: 403, message: "权限不足" } }
  });
}

async function flush() {
  await new Promise(resolve => setTimeout(resolve, 0));
}

describe("授权页类型候选降级判定", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    hasPermsMock.mockReturnValue(true);
    getTypeDefList.mockResolvedValue({ items: [] });
    getResourceTree.mockResolvedValue({ items: [] });
    getOperationList.mockResolvedValue({ items: [] });
    getConditionList.mockResolvedValue({ items: [] });
  });

  it("权限串缺 TYPE_VIEW：不发任何依赖请求，直接降级（理解 A 原行为锁定）", async () => {
    hasPermsMock.mockReturnValue(false);
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(true);
    expect(getTypeDefList).not.toHaveBeenCalled();
    expect(getResourceTree).not.toHaveBeenCalled();
  });

  it("type-definition/list 被 403 拒（仅实例级授权场景）：进降级块、不发其余依赖请求、不弹错（旧实现弹通用错误且无重试入口）", async () => {
    getTypeDefList.mockRejectedValue(axios403());
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(true);
    expect(hook.typeCandidates.value).toEqual([]);
    expect(messageMock).not.toHaveBeenCalled();
    expect(getResourceTree).not.toHaveBeenCalled();
    expect(getOperationList).not.toHaveBeenCalled();
  });

  it("type-definition/list 非 403 失败：维持通用错误提示，不进降级", async () => {
    getTypeDefList.mockRejectedValue(new Error("boom"));
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(false);
    expect(messageMock).toHaveBeenCalledTimes(1);
  });

  it("资源树被 403 拒：不进降级（树/操作列无前端前置的既定口径），走通用错误", async () => {
    getResourceTree.mockRejectedValue(axios403());
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(false);
    expect(messageMock).toHaveBeenCalledTimes(1);
  });
});
