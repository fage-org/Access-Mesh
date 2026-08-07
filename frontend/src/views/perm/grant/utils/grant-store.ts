/**
 * 权限授予页 Pinia store（T-FE-036 内新建，不复用现有全局 store，设计 §6.5）。
 *
 * 提交状态机（第十四轮收窄，四态 discriminated union）：
 * idle -> dirty -> saving ->（成功）idle /（失败）saveFailed；
 * 砍八态的 loading/ready/outcomeUnknown/stale + 代际号 + list 对比恢复 + 重放--
 * 内部管理页低频，超时由 saveFailed(unknownOutcome=true) 提示"网络异常，请刷新页面确认当前状态"覆盖。
 *
 * 正交性（第十一轮 P2-7）：页面 capability（edit/view）仅由门禁派生（ROLE:VIEW->view /
 * ROLE:MANAGE->edit）+ GROUP_ROLE 主体 -> view；AUTO_DEP 降为记录级 readonlyReason，
 * 不进入本状态机。
 *
 * baseline 迁移规则：baseline 只在 apply-grant-plan 明确成功后整体切换（响应 = 完整持久化结果）；
 * 请求失败 -> 全部条目保留标红、整体重试（后端单事务原子无部分成功）。
 *
 * 并发与原子性（评审问题 3+4）：
 * - baselineToken/saveToken 为 store 实例字段（非模块级全局，避免测试/HMR/多实例干扰），
 *   单调递增；resetAll 只递增失效，不归零。
 * - selectSubject 延迟设置 context：请求成功且 token 仍当前才原子提交
 *   context/baseline/changes/submit，避免"加载数据前更新上下文"的竞态；过期请求不写任何
 *   字段，finally 也先核对 token（不清掉后续请求的 baselineLoading）。
 * - saving 期间 applyChanges/revertChange/revertAll/selectSubject 返回 false 且不改状态
 *   （草稿/submit/context/baseline 冻结）；saveAll 二次调用直接 return false 不发请求。
 * - saveAll 响应除 token 外再核对捕获的 roleTypeCode + roleExternalId（双保险）。
 */
import { defineStore } from "pinia";
import {
  applyGrantPlan,
  getRolePermissionList,
  GRANT_ERROR_MESSAGES,
  type RolePermissionItem
} from "@/api/permission-grant";
import { RequestError } from "@/api/_envelope";
import { buildGrantPlan } from "./grant-plan";
import type {
  DraftChange,
  GrantContext,
  PageCapability,
  SubmitState
} from "./types";

export type GrantStoreState = {
  /** 主体上下文（GrantContext，左栏选中节点派生；domainCode 恒 null） */
  context: GrantContext | null;
  /** 页面能力（仅门禁派生，与状态机正交） */
  capability: PageCapability;
  /** 基线（list includeChildren=true 全量：主权限 + 子权限；来源链仅消费主权限） */
  baseline: RolePermissionItem[];
  /** 草稿变更（右栏清单；有序） */
  changes: DraftChange[];
  /** 提交状态（四态 discriminated union） */
  submit: SubmitState;
  /** 基线加载中（首进/切换主体/刷新） */
  baselineLoading: boolean;
  /** 基线请求代际令牌（单调递增；过期请求不写状态，问题 3） */
  baselineToken: number;
  /** 保存请求代际令牌（单调递增；过期请求不写状态，问题 4） */
  saveToken: number;
};

/**
 * 按当前 MatrixContext 过滤 apply-grant-plan 响应（T-FE-038 review P1-1）。
 * 响应 = 角色**完整**权限集合（契约 §6.5.1，mock 亦然），单类型矩阵 baseline 不变量为
 * "当前类型主权限 + 其全部子权限"（子权限按 depend_on 挂父、可跨类型，与 list 类型过滤
 * 语义一致，契约 §6.4）；不过滤会引入其他类型主权限，再次编辑全局 INSTANCE 操作时
 * 被识别为"取消勾选"进入 removes —— 误删其他类型权限。
 */
export function filterBaselineByType(
  items: RolePermissionItem[],
  resourceTypeCode: string
): RolePermissionItem[] {
  const mains = items.filter(
    r => r.dependOn == null && r.resourceTypeCode === resourceTypeCode
  );
  const mainIds = new Set(mains.map(r => r.id));
  return [
    ...mains,
    ...items.filter(r => r.dependOn != null && mainIds.has(r.dependOn))
  ];
}

/** 保存失败分类（对齐 _envelope ErrorKind 语义：明确拒绝白名单可安全重试，其余结果未知） */
export function classifySaveError(error: unknown): {
  message: string;
  unknownOutcome: boolean;
} {
  // 统一信封业务错（unwrap 抛出）：按错误码映射文案（DoD-1）
  if (error instanceof RequestError && error.kind === "business") {
    const mapped =
      error.appCode != null ? GRANT_ERROR_MESSAGES[error.appCode] : undefined;
    return {
      message: mapped ?? error.message ?? "保存失败，请重试",
      unknownOutcome: false
    };
  }
  // axios 层明确拒绝（服务端未提交，可安全重试）
  const status = (
    error as { response?: { status?: number; data?: { message?: string } } }
  )?.response?.status;
  if (status != null && [400, 401, 403, 409, 422].includes(status)) {
    const respMessage = (
      error as { response?: { data?: { message?: string } } }
    )?.response?.data?.message;
    return {
      message: respMessage ?? "保存被拒绝，请重试",
      unknownOutcome: false
    };
  }
  // 超时 / 网络 / 5xx / 未知来源 -> 结果未知：提示刷新确认，不做自动恢复 machinery
  return {
    message: "网络异常，请刷新页面确认当前状态",
    unknownOutcome: true
  };
}

/**
 * 拉取主体基线（selectSubject/loadBaseline/switchMatrixType 共用；问题 3 共享 baselineToken 代域）。
 * list includeChildren=true 一次取全量；resourceTypeCode 按当前矩阵类型过滤主权限（T-PERM-040 契约 §6.4）。
 */
async function fetchBaseline(
  context: GrantContext,
  resourceTypeCode: string | null
): Promise<RolePermissionItem[]> {
  const resp = await getRolePermissionList({
    domainCode: context.domainCode,
    roleTypeCode: context.roleTypeCode,
    roleExternalId: context.roleExternalId,
    resourceTypeCode,
    includeChildren: true
  });
  return resp.items ?? [];
}

export const useGrantStore = defineStore("perm-grant", {
  state: (): GrantStoreState => ({
    context: null,
    capability: "view",
    baseline: [],
    changes: [],
    submit: { kind: "idle" },
    baselineLoading: false,
    baselineToken: 0,
    saveToken: 0
  }),
  getters: {
    /** 是否存在未保存变更（右栏清单/底部保存条/离开保护） */
    isDirty: state => state.changes.length > 0,
    changeCount: state => state.changes.length,
    /** 主权限（dependOn==null；来源链计算输入） */
    mainRecords: state => state.baseline.filter(r => r.dependOn == null),
    /** 保存中（按钮 disabled 防重复点击，§6.5） */
    isSaving: state => state.submit.kind === "saving"
  },
  actions: {
    setCapability(capability: PageCapability) {
      this.capability = capability;
    },

    /**
     * 同步主体展示名（树刷新后角色改名，不重载 baseline，问题 6）。
     * 仅更新 context.displayName，不动 baseline/changes/submit（saving 期间由 onActivated
     * 不触发保证；本 action 自身不设 saving 守卫--纯展示字段同步）。
     */
    syncDisplayName(displayName: string) {
      if (this.context) {
        this.context = { ...this.context, displayName };
      }
    },

    /**
     * 预取主体基线（T-FE-038 review P2-4：无副作用，与资源树/操作列/类型标记并行读取）。
     * 共享 baselineToken 代域：预取期间交互所有权转移（cancelPending 等）→ 返回 null，
     * 数据不得用于提交；网络错误时过期请求静默（不抛），当前代则抛给页面层提示。
     * 不设置 baselineLoading（并行读阶段由页面 matrixLoading 呈现，防误拦类型切换）。
     */
    async prepareBaseline(
      context: GrantContext,
      resourceTypeCode: string | null
    ): Promise<RolePermissionItem[] | null> {
      const token = ++this.baselineToken;
      try {
        const items = await fetchBaseline(context, resourceTypeCode);
        if (token !== this.baselineToken) return null;
        return items;
      } catch (error) {
        if (token !== this.baselineToken) return null;
        throw error;
      }
    },

    /**
     * 提交已预取的主体（T-FE-038 review P2-4：prepareBaseline 成功后同步调用，无网络）。
     * 原子提交 context/baseline/changes/submit；saving 期间拒绝（返回 false，不改状态）。
     */
    commitSubject(context: GrantContext, items: RolePermissionItem[]): boolean {
      if (this.submit.kind === "saving") return false;
      this.context = context;
      this.baseline = items;
      this.changes = [];
      this.submit = { kind: "idle" };
      return true;
    },

    /**
     * 切换主体（评审问题 3：原子提交 + 代际令牌；问题 4：saving 冻结）。
     * saving 期间拒绝（返回 false，不改状态）；未保存变更拦截由页面层先行确认。
     * 延迟设置 context：请求成功且 token 仍当前才原子提交 context/baseline/changes/submit，
     * 避免加载数据前更新上下文导致竞态（过期请求不写任何字段）。
     * resourceTypeCode = 主体切换后的默认矩阵类型（§3.6 主体切换与类型切换共用加载管线）。
     * （T-FE-038 主体切换主路径已改用 prepareBaseline + commitSubject 并行化，本方法保留兼容）
     */
    async selectSubject(
      context: GrantContext,
      resourceTypeCode: string | null
    ): Promise<boolean> {
      if (this.submit.kind === "saving") return false;
      const token = ++this.baselineToken;
      this.baselineLoading = true;
      try {
        const items = await fetchBaseline(context, resourceTypeCode);
        // 过期请求不写状态（切换/刷新/重置竞争）
        if (token !== this.baselineToken) return false;
        // 原子提交：context 与 baseline/changes/submit 一起切换
        this.context = context;
        this.baseline = items;
        this.changes = [];
        this.submit = { kind: "idle" };
        return true;
      } catch (error) {
        // 过期请求不写状态；token 仍当前则抛错给页面层提示
        if (token !== this.baselineToken) return false;
        throw error;
      } finally {
        // finally 也先核对 token，避免过期请求清掉后续请求的 loading
        if (token === this.baselineToken) {
          this.baselineLoading = false;
        }
      }
    },

    /**
     * 加载/刷新基线（刷新当前主体；selectSubject 已原子提交 context）。
     * 共享 baselineToken 代域：刷新期间切换主体互不覆盖（问题 3）。
     * resourceTypeCode = 当前矩阵类型（刷新保持类型过滤）。
     */
    async loadBaseline(resourceTypeCode: string | null): Promise<boolean> {
      const context = this.context as GrantContext | null;
      if (!context) return false;
      const token = ++this.baselineToken;
      this.baselineLoading = true;
      try {
        const items = await fetchBaseline(context, resourceTypeCode);
        if (token !== this.baselineToken) return false;
        this.baseline = items;
        this.changes = [];
        this.submit = { kind: "idle" };
        return true;
      } catch (error) {
        if (token !== this.baselineToken) return false;
        throw error;
      } finally {
        if (token === this.baselineToken) {
          this.baselineLoading = false;
        }
      }
    },

    /**
     * 切换矩阵类型（T-FE-038 §3.6 类型切换加载流程）。
     * 与 selectSubject 的差异：已确认放弃 → **立即清空**旧 baseline/草稿/submit（§3.6 步骤 2），
     * 新数据加载失败不回滚（旧类型视图不再恢复）；资源树/操作列由页面层并行清空与填充。
     * 共享 baselineToken 代域：快速连续切换时旧类型迟到响应被丢弃（请求序号守卫，S8-8）。
     */
    async switchMatrixType(resourceTypeCode: string): Promise<boolean> {
      const context = this.context as GrantContext | null;
      if (this.submit.kind === "saving" || !context) return false;
      const token = ++this.baselineToken;
      this.baseline = [];
      this.changes = [];
      this.submit = { kind: "idle" };
      this.baselineLoading = true;
      try {
        const items = await fetchBaseline(context, resourceTypeCode);
        if (token !== this.baselineToken) return false;
        this.baseline = items;
        return true;
      } catch (error) {
        if (token !== this.baselineToken) return false;
        throw error;
      } finally {
        if (token === this.baselineToken) {
          this.baselineLoading = false;
        }
      }
    },

    /**
     * 作废在途请求（T-FE-038 review P1-1：交互所有权转移时调用）。
     * 仅递增 baselineToken 使在途 selectSubject/loadBaseline/switchMatrixType 的迟到响应被丢弃；
     * 被作废请求的 finally 不会清理 loading（token 已过期），故此处同步复位 baselineLoading，
     * 避免页面冻结；不发起新请求、不改其他任何状态（与 resetAll 的差异：保留 context/baseline/changes）。
     */
    cancelPending() {
      this.baselineToken += 1;
      this.baselineLoading = false;
    },

    /**
     * 应用草稿变更集（diff 结果）；空变更集回到 idle。
     * saving 期间拒绝（问题 4 冻结草稿）：返回 false，changes/submit 不变。
     */
    applyChanges(next: DraftChange[]): boolean {
      if (this.submit.kind === "saving" || this.baselineLoading) return false;
      this.changes = next;
      this.submit = next.length > 0 ? { kind: "dirty" } : { kind: "idle" };
      return true;
    },

    /**
     * 逐条撤销（右栏清单）；草稿父被撤销时其虚拟挂载子权限一并撤销。
     * saving 期间拒绝（问题 4）：返回 false，changes 不变。
     */
    revertChange(changeId: string): boolean {
      if (this.submit.kind === "saving" || this.baselineLoading) return false;
      const target = this.changes.find(c => c.changeId === changeId);
      if (!target) return false;
      const cascadeIds = new Set<string>([changeId]);
      if (target.kind === "add") {
        for (const c of this.changes) {
          if (c.kind === "add" && c.parentChangeId === changeId) {
            cascadeIds.add(c.changeId);
          }
        }
      }
      return this.applyChanges(
        this.changes.filter(c => !cascadeIds.has(c.changeId))
      );
    },

    /**
     * 放弃全部（回滚为 baseline）。saving 期间拒绝（问题 4）。
     */
    revertAll(): boolean {
      return this.applyChanges([]);
    },

    /**
     * 保存全部 = 单请求 apply-grant-plan（唯一写入口，§6.4）。
     * 成功 -> 全部条目移出清单并入 baseline（响应整体替换）；
     * 失败 -> 全部条目保留标红、整体重试（请求粒度，不可分割）。
     * 代际令牌 + 上下文身份双保险（问题 4）：过期响应不覆盖 baseline/changes/submit。
     * resourceTypeCode = 当前矩阵类型（T-FE-038）：响应按类型过滤为
     * "当前类型主权限 + 其全部子权限"（filterBaselineByType），null/缺省 = 不过滤。
     */
    async saveAll(resourceTypeCode: string | null = null): Promise<boolean> {
      if (this.submit.kind === "saving" || this.baselineLoading) return false;
      const context = this.context as GrantContext | null;
      if (!context) return false;
      const plan = buildGrantPlan(this.changes);
      if (!plan) return false;
      const token = ++this.saveToken;
      // 捕获上下文身份：响应回来核对（saving 期间 selectSubject 被拒，理论上不变；双保险）
      const roleTypeCode = context.roleTypeCode;
      const roleExternalId = context.roleExternalId;
      this.submit = { kind: "saving" };
      try {
        const resp = await applyGrantPlan({
          domainCode: context.domainCode,
          roleTypeCode,
          roleExternalId,
          plan
        });
        // 过期请求（二次保存/重置竞争）不写状态
        if (token !== this.saveToken) return false;
        // 上下文身份双保险
        if (
          this.context?.roleTypeCode !== roleTypeCode ||
          this.context?.roleExternalId !== roleExternalId
        ) {
          return false;
        }
        this.baseline = resourceTypeCode
          ? filterBaselineByType(resp.items ?? [], resourceTypeCode)
          : (resp.items ?? []);
        this.changes = [];
        this.submit = { kind: "idle" };
        return true;
      } catch (error) {
        if (token !== this.saveToken) return false;
        const classified = classifySaveError(error);
        this.submit = { kind: "saveFailed", ...classified };
        return false;
      }
    },

    /**
     * 页面卸载重置（离开页面清空上下文与草稿）。
     * 递增 baselineToken/saveToken 让在途请求失效（不归零，保持单调，问题 3+4）。
     */
    resetAll() {
      this.baselineToken += 1;
      this.saveToken += 1;
      this.context = null;
      this.capability = "view";
      this.baseline = [];
      this.changes = [];
      this.submit = { kind: "idle" };
      this.baselineLoading = false;
    }
  }
});

// 注：不提供 useGrantStoreHook--本 store 仅在 4.1 页组件树内使用（含 onBeforeRouteLeave
// 组件内守卫），避免模块级依赖 @/store -> router 链（测试环境 import.meta.env 缺失）。
