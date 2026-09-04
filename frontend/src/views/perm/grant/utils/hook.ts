/**
 * 权限授予页（4.1 v3）编排 composable。
 * 职责：只读依赖加载（资源树/操作/条件）→ 门控派生 → 生效视图/来源链计算 →
 * 弹窗/详情层/变更清单事件编排 → 保存/放弃/离开保护。
 * 状态归属：baseline + 草稿 + 提交状态机在 grant-store（Pinia 四态）；本 hook 只做编排与派生。
 */
import {
  computed,
  onActivated,
  onMounted,
  onBeforeUnmount,
  ref,
  watch
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import {
  getOperationList,
  getResourceTree,
  type ResourceTreeNode
} from "@/api/resource-operation";
import {
  getConditionList,
  type ConditionResp
} from "@/api/permission-condition";
import { getTypeDefList, TYPE_KEY, type TypeDefResp } from "@/api/type-def";
import { getRolePermissionList } from "@/api/permission-grant";
import { PERMISSION_GRANT_PERMS } from "./perms";
import { useGrantStore } from "./grant-store";
import {
  applyDraftToRecords,
  parentLocateOf,
  persistedParentKey,
  draftParentKey,
  type EffectiveRecord
} from "./grant-plan";
import {
  computeSourceChain,
  coveredSetOf,
  mergeOperationsForType,
  sortOperationDefs,
  type OperationDefInput
} from "./source-chain";
import { decideRefreshAction, isSamePresetSubject } from "./subject-tree";
import type {
  DraftChange,
  GrantContext,
  SubjectType,
  SubjectTreeNode
} from "./types";

/** 操作列显示配置 localStorage 前缀（🔧 T-FE-038：按 resourceTypeCode 隔离，§3.2；原按 subjectType 隔离的 "perm-grant:columns" 键不再使用，§13.2 决策 7 修订） */
const COLUMN_STORAGE_PREFIX = "permission-grant:hidden-columns:";

/** 上次选择类型 localStorage 前缀（按 subjectType 隔离，§2.2 默认选中上次选择类型） */
const LAST_TYPE_STORAGE_PREFIX = "perm-grant:last-type:";

export function usePermissionGrant() {
  const route = useRoute();
  const router = useRouter();
  const grantStore = useGrantStore();

  // ========== 主体入口（§1.1：角色/组织两入口共用；PERSONAL 预留不挂路由） ==========

  const subjectType = computed<SubjectType>(() =>
    route.query.subjectType === "ORG" ? "ORG" : "ROLE"
  );

  /** 主体称呼（提示文案用，组织入口含岗位主体） */
  const subjectLabel = computed(() =>
    subjectType.value === "ORG" ? "组织/岗位" : "角色"
  );

  // ========== 门控（§10 双层门禁；页面 capability 仅门禁派生） ==========

  const canView = computed(() => hasPerms(PERMISSION_GRANT_PERMS.ROLE_VIEW));
  const canManage = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.ROLE_MANAGE)
  );
  // ========== 只读依赖（首次加载；切换主体/类型不重复拉取） ==========

  /**
   * 资源类型定义全集（type-definition/list 筛选 type_key=resource_type，§2.2）。
   * 候选 = **全集**，不取主体已有权限类型（无权限主体才能完成首次授权，防死锁 S11）。
   */
  const typeCandidates = ref<TypeDefResp[]>([]);
  /** 全量资源森林（授权弹窗子权限配置器与详情层使用；子权限可跨类型） */
  const allResourceForest = ref<ResourceTreeNode[]>([]);
  /** 全量操作定义（子权限按所选资源类型本地合并） */
  const allOperationDefs = ref<OperationDefInput[]>([]);
  /** 当前矩阵类型资源树（§3.6 按类型查询；驱动矩阵行/ALL 虚拟行/授权弹窗资源树） */
  const resourceForest = ref<ResourceTreeNode[]>([]);
  /**
   * 当前矩阵类型操作定义（operation-permission/list 携带 resourceTypeCode；
   * 全局操作概念已退役，操作定义按类型完全隔离，§3.2）。
   * 切换类型必须重新查询，禁止复用上一类型。
   */
  const operationDefs = ref<OperationDefInput[]>([]);
  /** 当前矩阵类型（MatrixContext，§2.2：资源树/操作列/主权限记录/ALL 行/继承计算/列配置全部绑定） */
  const currentTypeCode = ref<string | null>(null);
  /** 类型加载中（§3.6 中栏统一骨架屏）；matrixToken 序号守卫：快速连续切换丢弃旧请求响应（S8-8） */
  const matrixLoading = ref(false);
  /**
   * 交互/加载统一代际（T-FE-038 review P1-1）：主体选择（含 no-op）、分组切换、类型切换、
   * 加载管线入口统一递增，所有 await 后校验同一代际——任何新交互作废全部在途请求
   * （store 层在途由 cancelPending 作废 baselineToken；hook 层在途由本代际作废）。
   */
  let matrixToken = 0;
  /** 主体已有权限类型（§2.2 仅用于下拉标记与排序——有权限的排前；主体切换时全量主权限查询一次） */
  const subjectPermissionTypes = ref<string[]>([]);
  const conditions = ref<ConditionResp[]>([]);
  const depsLoading = ref(false);
  let depsLoaded = false;
  /**
   * 🔧 T-FE-018（理解 A，2026-09-02 定案）：当前用户缺少 TYPE_DEFINITION:VIEW 软依赖。
   * true 时类型下拉与矩阵区禁用并显示「权限不足+重试」（GrantMatrixPanel），
   * 不误报为「暂无资源类型配置」空态；页面其余部分（主体树等）保持可用。
   */
  const typePermDenied = ref(false);

  /** 默认矩阵类型：上次选择（localStorage 按 subjectType 隔离）优先，否则候选第一个（§2.2） */
  function resolveDefaultTypeCode(): string | null {
    if (typeCandidates.value.length === 0) return null;
    const last = localStorage.getItem(
      `${LAST_TYPE_STORAGE_PREFIX}${subjectType.value}`
    );
    if (last && typeCandidates.value.some(t => t.typeCode === last)) {
      return last;
    }
    return typeCandidates.value[0].typeCode;
  }

  async function loadDeps() {
    if (depsLoaded || depsLoading.value) return;
    depsLoading.value = true;
    try {
      // 🔧 T-FE-018（理解 A）：类型候选只读依赖探查——后端 type-definition/list 强制
      // TYPE_DEFINITION:VIEW（类型级），缺权限时 loadDeps 会整体失败。缺权限不发起
      // 四个依赖请求（全部服务于矩阵区，已禁用），仅标记降级态；重试入口见 retryLoadDeps。
      if (!hasPerms(PERMISSION_GRANT_PERMS.TYPE_VIEW)) {
        typePermDenied.value = true;
        typeCandidates.value = [];
        return;
      }
      typePermDenied.value = false;
      // 🔧 T-FE-018 评审补（2026-09-03 终案）：后端 type-definition/list 门禁已放宽为
      // 「类型级或任一实例级 VIEW」（与登录权限串投影口径对齐）；本端 403 捕获保留为
      // 防御层（覆盖未来门禁变化）——权限拒绝进入降级块（可重试），不落「暂无资源类型
      // 配置」空态、不弹通用错误；其余错误（含资源树/操作列 403——二者无前端前置为
      // 既定口径）维持原路径。
      let typeResp: Awaited<ReturnType<typeof getTypeDefList>>;
      try {
        typeResp = await getTypeDefList({ typeKey: TYPE_KEY.RESOURCE_TYPE });
      } catch (error: any) {
        if (error?.response?.status === 403 || error?.appCode === 403) {
          typePermDenied.value = true;
          typeCandidates.value = [];
          return;
        }
        throw error;
      }
      const [treeResp, opResp, conditionResp] = await Promise.all([
        // 门控说明（T-ACCESS-021 GUI 段缺陷修复）：资源树/操作列不做前端 capability 前置
        // （访问控制由后端类型级 VIEW 门禁 T-PERM-042 承担，无权限者收到接口错误提示；
        // RESOURCE/OPERATION 虽已随 T-PERM-025 补入 /auth/user-menu 权限串白名单，
        // 仍维持不做前端前置的既有设计）
        getResourceTree({}),
        getOperationList({}),
        // 🔧 T-FE-040 v3.1（S5）：条件查看全租户开放（2026-08-08 产品确认），条件列表始终加载
        getConditionList()
      ]);
      typeCandidates.value = (typeResp.items ?? [])
        .filter(t => t.typeKey === TYPE_KEY.RESOURCE_TYPE)
        .sort((a, b) => a.sortOrder - b.sortOrder);
      allResourceForest.value = (treeResp.items ?? [])
        .map(it => it.root)
        .filter(Boolean) as ResourceTreeNode[];
      allOperationDefs.value = (opResp.items ?? []).map(op => ({
        code: op.code,
        name: op.name,
        resourceTypeCode: op.resourceTypeCode,
        binaryBit: op.binaryBit,
        inheritMask: op.inheritMask
      }));
      conditions.value = conditionResp.items ?? [];
      depsLoaded = true;
    } catch (error: any) {
      message(error.message || "加载基础数据失败", { type: "error" });
    } finally {
      depsLoading.value = false;
    }
    // 候选就绪后确定默认类型（挂载时 currentTypeCode 尚为 null）
    if (currentTypeCode.value == null) {
      const typeCode = resolveDefaultTypeCode();
      if (typeCode != null) currentTypeCode.value = typeCode;
    }
    // 挂载竞态兜底：主体在候选就绪前已选中（空候选路径补加载因候选未就绪跳过）→
    // 候选就绪后矩阵仍空则补加载（统一代际后任何主体选择都推进 matrixToken，
    // 原 matrixToken===0 条件恒假失效；改用矩阵空态判定）
    if (
      grantStore.context != null &&
      currentTypeCode.value != null &&
      !matrixLoading.value &&
      resourceForest.value.length === 0
    ) {
      loadMatrixForType({ typeCode: currentTypeCode.value }).catch(() => {});
    }
  }

  /**
   * 🔧 T-FE-018（理解 A）：typePermDenied 降级态的重试入口——重新探查权限串
   * （权限授予后无需重新登录即可生效于下一次登录态刷新；重试本身幂等）。
   */
  function retryLoadDeps() {
    void loadDeps();
  }

  // ========== 查看态（§3.4 开关为查看态过滤，不影响草稿与数据） ==========

  const includeResourceInherit = ref(true);
  const includeOpInherit = ref(true);
  const resourceKeyword = ref("");

  /**
   * 隐藏操作列集合（🔧 T-FE-038：按 resourceTypeCode 隔离，存储键
   * `permission-grant:hidden-columns:{resourceTypeCode}`，禁止跨类型共享配置，§3.2）。
   * 类型切换后读取/写入对应键。
   */
  const hiddenColumnCodes = ref<string[]>([]);

  function loadHiddenColumns(typeCode: string | null): string[] {
    if (typeCode == null) return [];
    try {
      const raw = localStorage.getItem(`${COLUMN_STORAGE_PREFIX}${typeCode}`);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed)
        ? parsed.filter(x => typeof x === "string")
        : [];
    } catch {
      return [];
    }
  }

  /** 类型切换 → 读取该类型的隐藏列配置 */
  watch(
    currentTypeCode,
    typeCode => {
      hiddenColumnCodes.value = loadHiddenColumns(typeCode);
    },
    { immediate: true }
  );

  /** 当前类型持久化（§2.2 默认选中上次选择的类型，localStorage 按 subjectType 隔离） */
  watch(currentTypeCode, typeCode => {
    if (typeCode != null) {
      localStorage.setItem(
        `${LAST_TYPE_STORAGE_PREFIX}${subjectType.value}`,
        typeCode
      );
    }
  });

  /** 隐藏列变化 → 写回当前类型键 */
  watch([hiddenColumnCodes, currentTypeCode], ([, typeCode]) => {
    if (typeCode != null) {
      localStorage.setItem(
        `${COLUMN_STORAGE_PREFIX}${typeCode}`,
        JSON.stringify(hiddenColumnCodes.value)
      );
    }
  });

  /** 操作定义删除/调整后清理当前类型配置中已不存在的操作码（§3.2，按 code 比对） */
  function pruneHiddenColumns(ops: OperationDefInput[]): void {
    if (hiddenColumnCodes.value.length === 0) return;
    const valid = new Set(ops.map(op => op.code));
    const pruned = hiddenColumnCodes.value.filter(code => valid.has(code));
    if (pruned.length !== hiddenColumnCodes.value.length) {
      hiddenColumnCodes.value = pruned;
    }
  }

  // ========== 生效视图 + 来源链（§3.5 前端自算） ==========

  const effective = computed(() =>
    applyDraftToRecords({
      baseline: grantStore.baseline,
      changes: grantStore.changes,
      operations: operationDefs.value
    })
  );

  const sourceChain = computed(() =>
    computeSourceChain({
      records: effective.value.mains,
      resources: resourceForest.value,
      operations: operationDefs.value,
      includeResourceInherit: includeResourceInherit.value,
      includeOpInherit: includeOpInherit.value
    })
  );

  /**
   * 操作列 = 当前类型操作定义（operationDefs 即 operation-permission/list 按类型的
   * 响应；全局操作概念已退役）。单类型矩阵上下文不跨类型 union。
   */
  const unionColumns = computed(() =>
    operationDefs.value.map(op => ({
      code: op.code,
      name: op.name
    }))
  );

  /** 可见操作列（操作列配置过滤） */
  const visibleColumns = computed(() =>
    unionColumns.value.filter(c => !hiddenColumnCodes.value.includes(c.code))
  );

  // ========== 主体选择（未保存变更拦截，§6.5 离开保护同源） ==========

  async function confirmDiscardIfDirty(): Promise<boolean> {
    if (!grantStore.isDirty) return true;
    try {
      await ElMessageBox.confirm(
        "当前存在未保存的授权变更，切换/离开将放弃这些变更，是否继续？",
        "未保存变更",
        {
          confirmButtonText: "放弃变更",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
      return true;
    } catch {
      return false;
    }
  }

  async function handleSelectSubject(context: GrantContext): Promise<boolean> {
    if (
      grantStore.context?.roleTypeCode === context.roleTypeCode &&
      grantStore.context?.roleExternalId === context.roleExternalId
    ) {
      // 同主体 no-op（review P1-1 场景 1）：当前主体 A 的点击必须作废在途的
      // 后选主体请求——A 只推进 hook 代际时 B 的 selectSubject 仍会提交 context=B，
      // 树高亮 A 但保存作用于 B。故此处同时作废 store 层在途（cancelPending）；
      // 在途加载的 finally 因代际过期不清理 → 同步复位 matrixLoading 防骨架屏卡死
      ++matrixToken;
      matrixLoading.value = false;
      grantStore.cancelPending();
      // 消费残留 query（评审问题 5 防御）
      if (route.query.roleExternalId === context.roleExternalId) {
        router.replace({
          query: { ...route.query, roleExternalId: undefined }
        });
      }
      // T-FE-038：上次加载失败但 context 已提交（矩阵空）→ 重试加载当前类型
      if (
        !matrixLoading.value &&
        currentTypeCode.value != null &&
        resourceForest.value.length === 0
      ) {
        loadMatrixForType({ typeCode: currentTypeCode.value }).catch(() => {});
      }
      return true;
    }
    // 确认弹窗（模态）期间在途请求可正常完成；确认通过后才取得交互所有权
    if (!(await confirmDiscardIfDirty())) return false;
    const gen = ++matrixToken;
    // 在途加载的 finally 因代际过期不清理 → 同步复位（防骨架屏卡死）
    matrixLoading.value = false;
    grantStore.cancelPending();
    // §2.2 默认矩阵类型：上次选择（localStorage 按 subjectType 隔离）或候选第一个；
    // 候选全集为空 → 仅提交主体上下文，矩阵区 el-empty 空态（S8：候选为空）
    const typeCode = resolveDefaultTypeCode();
    if (typeCode == null) {
      try {
        // review P1-2/P2-4：类型标记 + 主体基线**并行预取**（均无副作用）→ 全部成功
        // 后 commitSubject 原子提交；任一读失败时 context 未提交（杜绝部分提交）
        const [markResp, baselineItems] = await Promise.all([
          getRolePermissionList({
            domainCode: context.domainCode,
            roleTypeCode: context.roleTypeCode,
            roleExternalId: context.roleExternalId,
            includeChildren: false
          }),
          grantStore.prepareBaseline(context, null)
        ]);
        // 代际校验：读请求期间新交互（cancelPending 使预取过期返回 null）→ 放弃
        if (gen !== matrixToken) return false;
        if (baselineItems === null) return false;
        if (!grantStore.commitSubject(context, baselineItems)) return false;
        // 提交成功才写入"已有权限类型"标记
        const types = new Set<string>();
        for (const item of markResp.items ?? []) {
          types.add(item.resourceTypeCode);
        }
        subjectPermissionTypes.value = [...types].sort();
      } catch (error: any) {
        // 代际校验：过期请求的失败提示无意义（且不得回滚后选主体的状态）
        if (gen !== matrixToken) return false;
        message(error.message || "加载权限配置失败", { type: "error" });
        return false;
      }
      // 补加载（T-FE-038 review P1-2）：候选可能在标记/提交期间才就绪——
      // 本流程尚未触发加载（matrixToken === gen）则补触发；补加载自身递增一次（gen+1）
      if (gen === matrixToken) {
        const resolved = resolveDefaultTypeCode();
        if (resolved != null) {
          currentTypeCode.value = resolved;
          try {
            await loadMatrixForType({ typeCode: resolved });
          } catch (error: any) {
            // 仅允许本流程的这一次补加载（gen+1）；期间新交互 → 放弃提示
            if (matrixToken !== gen + 1) return false;
            message(error.message || "加载权限配置失败", { type: "error" });
          }
        }
      }
      // 后处理守卫：恰好允许本流程的补加载（gen+1）；任何新交互 → 放弃 setCapability/query 消费
      if (matrixToken !== gen && matrixToken !== gen + 1) return false;
      grantStore.setCapability(canManage.value ? "edit" : "view");
      if (route.query.roleExternalId === context.roleExternalId) {
        router.replace({
          query: { ...route.query, roleExternalId: undefined }
        });
      }
      return true;
    }
    const prevTypeCode = currentTypeCode.value;
    currentTypeCode.value = typeCode;
    try {
      // 主体切换 + 默认类型：loadMatrixForType 内"读请求全部成功后才统一提交主体"（review P1-2）
      const ok = await loadMatrixForType({
        typeCode,
        subject: context
      });
      if (!ok) {
        // 代际校验：加载期间新交互（类型切换等）→ 放弃回滚（不得取消后选类型）
        if (matrixToken !== gen + 1) return false;
        // store 拒绝（saving 冻结等）→ 回滚类型选择，
        // 避免"下拉已切新类型 + 矩阵空（树/操作列已清空）"不一致态
        currentTypeCode.value = prevTypeCode;
        return false;
      }
      // 代际校验：过期则放弃后处理（后选主体的流程会设置自己的 capability/消费 query）
      if (matrixToken !== gen + 1) return false;
    } catch (error: any) {
      // 代际校验：过期不回滚 currentTypeCode（可能已被后选主体/类型设置）
      if (matrixToken !== gen + 1) return false;
      currentTypeCode.value = prevTypeCode;
      message(error.message || "加载权限配置失败", { type: "error" });
      return false;
    }
    grantStore.setCapability(canManage.value ? "edit" : "view");
    // 一次性入口指令消费：预选成功后移除 query（问题 6）
    if (route.query.roleExternalId === context.roleExternalId) {
      router.replace({
        query: { ...route.query, roleExternalId: undefined }
      });
    }
    return true;
  }

  /**
   * 矩阵数据加载管线（§3.6 类型切换与主体切换共用）。
   * - 步骤 1（未保存变更确认）/步骤 2（清空旧数据）由调用方与 store action 完成；
   *   本函数负责：清空旧矩阵/旧操作列 → 三类数据并行查询
   *   （角色权限记录 store action + 当前类型资源树 + 当前类型操作列）→ 全部完成后构建。
   * - matrixToken 请求序号守卫（S8-8）：快速连续切换 A→B→C，A/B 迟到响应被丢弃。
   * - subject 传入 = 主体切换（store.selectSubject 原子提交 context）；缺省 = 类型切换
   *   （store.switchMatrixType 立即清空 baseline）。
   */
  async function loadMatrixForType(opts: {
    typeCode: string;
    subject?: GrantContext;
  }): Promise<boolean> {
    const token = ++matrixToken;
    matrixLoading.value = true;
    // 旧类型详情层数据已失效 → 关闭（T-FE-038：详情层记录绑定旧类型）
    drawerVisible.value = false;
    // §3.6 步骤 2：清空旧矩阵（资源行 + ALL 虚拟行 + 单元格状态）与旧操作列
    resourceForest.value = [];
    operationDefs.value = [];
    try {
      const subject = opts.subject ?? grantStore.context;
      if (!subject) return false;
      if (opts.subject) {
        // 主体切换（review P1-2 + P2-4）：四类读取**并行**（资源树/操作列/类型标记/
        // prepareBaseline 无副作用预取主体权限记录），全部成功并校验代际后才 commitSubject
        // 原子提交（context+baseline，无网络）；任一读失败整体失败、context 不提交，
        // 杜绝"树/操作/标记失败但主体已切换"的部分提交
        const [treeResp, opResp, markResp, baselineItems] = await Promise.all([
          getResourceTree({ resourceTypeCode: opts.typeCode }),
          getOperationList({ resourceTypeCode: opts.typeCode }),
          // 全量主权限查询一次 → "已有权限类型"标记（§2.2 标记/排序；
          // 缺省 resourceTypeCode 返回全量，兼容契约 §6.4）
          getRolePermissionList({
            domainCode: opts.subject.domainCode,
            roleTypeCode: opts.subject.roleTypeCode,
            roleExternalId: opts.subject.roleExternalId,
            includeChildren: false
          }),
          grantStore.prepareBaseline(opts.subject, opts.typeCode)
        ]);
        // 序号守卫：读取期间新交互（cancelPending 使预取过期返回 null）→ 不提交
        if (token !== matrixToken) return false;
        if (baselineItems === null) return false;
        if (!grantStore.commitSubject(opts.subject, baselineItems))
          return false;
        resourceForest.value = (treeResp.items ?? [])
          .map(it => it.root)
          .filter(Boolean) as ResourceTreeNode[];
        operationDefs.value = sortOperationDefs(
          (opResp.items ?? []).map(op => ({
            code: op.code,
            name: op.name,
            resourceTypeCode: op.resourceTypeCode,
            binaryBit: op.binaryBit,
            inheritMask: op.inheritMask
          }))
        );
        // 操作列配置清理：当前类型操作定义中已不存在的操作码（§3.2）
        pruneHiddenColumns(operationDefs.value);
        const types = new Set<string>();
        for (const item of markResp.items ?? []) {
          types.add(item.resourceTypeCode);
        }
        subjectPermissionTypes.value = [...types].sort();
        return true;
      }
      // 类型切换：switchMatrixType（立即清空 baseline）与读请求并行（不提交 context，
      // 无部分提交问题；失败不回滚，§3.6 步骤 2 语义）
      const [storeOk, treeResp, opResp] = await Promise.all([
        grantStore.switchMatrixType(opts.typeCode),
        getResourceTree({ resourceTypeCode: opts.typeCode }),
        getOperationList({ resourceTypeCode: opts.typeCode })
      ]);
      // 序号守卫：过期请求不写任何状态
      if (token !== matrixToken) return false;
      // store 拒绝（saving 冻结/无 context/内部过期）：丢弃树/操作，避免与新旧 baseline 不一致
      if (!storeOk) return false;
      resourceForest.value = (treeResp.items ?? [])
        .map(it => it.root)
        .filter(Boolean) as ResourceTreeNode[];
      operationDefs.value = sortOperationDefs(
        (opResp.items ?? []).map(op => ({
          code: op.code,
          name: op.name,
          resourceTypeCode: op.resourceTypeCode,
          binaryBit: op.binaryBit,
          inheritMask: op.inheritMask
        }))
      );
      // 操作列配置清理：当前类型操作定义中已不存在的操作码（§3.2）
      pruneHiddenColumns(operationDefs.value);
      return true;
    } catch (error) {
      if (token !== matrixToken) return false;
      throw error;
    } finally {
      if (token === matrixToken) {
        matrixLoading.value = false;
      }
    }
  }

  /** 类型切换（§3.6）：未保存变更确认（取消则中止）→ 清空 → 并行查询 → 构建 → 骨架屏 */
  async function handleSwitchType(typeCode: string): Promise<boolean> {
    if (typeCode === currentTypeCode.value) return true;
    if (grantStore.isSaving) {
      message("正在保存中，请稍后再切换", { type: "warning" });
      return false;
    }
    // review P1-1：仅用 selectingKey 判断"主体切换中"——baselineLoading 同时由
    // switchMatrixType 设置（类型 B 加载中选 C 时误拦，违反 A→B→C 最终为 C 验收），
    // 不能用共享的 baselineLoading；主体切换保护由 selectingKey（P1-2 按 key 值清理）
    // 独立承担，快速类型切换不受主体基线加载影响
    if (selectingKey.value != null) {
      message("主体切换中，请稍后再切换类型", { type: "warning" });
      return false;
    }
    if (!(await confirmDiscardIfDirty())) return false;
    currentTypeCode.value = typeCode;
    try {
      const ok = await loadMatrixForType({ typeCode });
      if (!ok) return false;
      return true;
    } catch (error: any) {
      message(error.message || "加载类型数据失败", { type: "error" });
      return false;
    }
  }

  // ========== 主体选择 UI 状态（受控协议，问题 3+6） ==========

  type SubjectTreeExpose = {
    loadTree: () => Promise<void>;
    findNode: (
      externalId: string,
      roleTypeCode?: string
    ) => SubjectTreeNode | null;
    preselect: (externalId: string) => void;
  };
  const subjectTreeRef = ref<SubjectTreeExpose | null>(null);
  const activeKey = ref<string | null>(null);
  const selectingKey = ref<string | null>(null);
  const groupHint = ref<string | null>(null);
  const refreshInFlight = ref(false);

  /** 冻结态（saving/切换期间，评审问题 3+4：禁用所有写入口） */
  const frozen = computed(
    () =>
      grantStore.isSaving ||
      grantStore.baselineLoading ||
      selectingKey.value != null
  );

  /**
   * 主体选择调用序号（review 复核 should-fix）：selectingKey 清理仅由最新调用执行——
   * 同主体读取在途时再次点击（树面板仅 isSaving 时 disabled），旧流程的 finally 不得
   * 清掉新流程的选择状态（否则窗口期 selectingKey=null + baselineLoading=false 时
   * 类型切换保护被绕过，切类型作废新流程的读取 → 主体切换静默失败）
   */
  let selectSeq = 0;

  async function onSelectSubject(payload: {
    key: string;
    context: GrantContext;
  }) {
    const seq = ++selectSeq;
    selectingKey.value = payload.key;
    try {
      const ok = await handleSelectSubject(payload.context);
      if (ok) {
        activeKey.value = payload.key;
        groupHint.value = null;
      }
    } finally {
      // 仅最新调用清理（过期流程的 finally 不清新流程的选择状态）
      if (seq === selectSeq) {
        selectingKey.value = null;
      }
    }
  }

  async function onSelectGroup(payload: { key: string; name: string }) {
    if (!(await confirmDiscardIfDirty())) return;
    // 交互所有权转移（review P1-1 场景 2）：空候选主体 A 的标记/补加载在途时切分组，
    // 必须作废其全部后处理（否则 A 仍会恢复并重新激活矩阵）——hook 代际 + store 在途双作废；
    // 同步复位 matrixLoading/baselineLoading（被作废请求的 finally 不再清理）
    ++matrixToken;
    matrixLoading.value = false;
    grantStore.cancelPending();
    selectingKey.value = payload.key;
    try {
      grantStore.resetAll();
      activeKey.value = payload.key;
      groupHint.value = payload.name;
    } finally {
      selectingKey.value = null;
    }
  }

  /**
   * 刷新树 + 预选/当前主体处理（问题 6）。
   * single-flight 防止 onMounted/onActivated/watch 并发重复加载。
   */
  async function refreshAndPreset() {
    if (refreshInFlight.value) return;
    refreshInFlight.value = true;
    try {
      await subjectTreeRef.value?.loadTree();
      const externalId = route.query.roleExternalId;
      const hasQuery = typeof externalId === "string" && externalId;
      const ctx = grantStore.context;
      // 无 query 时才查当前主体角色是否仍在树中（有 query 走 preset 分支）
      const node =
        !hasQuery && ctx
          ? (subjectTreeRef.value?.findNode(
              ctx.roleExternalId,
              ctx.roleTypeCode
            ) ?? null)
          : null;
      const action = decideRefreshAction({
        queryExternalId: externalId,
        context: ctx,
        node
      });
      switch (action.kind) {
        case "preset": {
          // 角色入口限定 BASIC_ROLE；组织入口不限（sys_org.id 全表唯一，组织/岗位共用 id 空间）
          const found = subjectTreeRef.value?.findNode(
            action.externalId,
            subjectType.value === "ORG" ? undefined : "BASIC_ROLE"
          );
          if (!found) {
            message(`未找到指定${subjectLabel.value}，请重新选择`, {
              type: "warning"
            });
            break;
          }
          if (isSamePresetSubject(grantStore.context, found)) {
            // 同主体 no-op：同步名称 + 消费 query（不重载 baseline，评审问题 5）；
            // 双字段比对防跨入口 id 碰撞（角色业务键 "1" vs 组织 id "1"，评审 P1）
            grantStore.syncDisplayName(found.name);
            router.replace({
              query: { ...route.query, roleExternalId: undefined }
            });
          } else {
            // 不同主体：preselect -> handleSelectSubject -> selectSubject + router.replace
            subjectTreeRef.value?.preselect(action.externalId);
          }
          break;
        }
        case "syncName":
          // 角色仍存在：同步改名，不重载 baseline（问题 6）
          grantStore.syncDisplayName(action.displayName);
          // EXTRA_ROLE 选中刷新后高亮转移到顶层 BASIC_ROLE（评审问题 1B）
          if (ctx?.fromGroupRoleName != null && node) {
            activeKey.value = node.key;
          }
          break;
        case "clearSubject":
          // 主体已删除：清空主体 + 提示（review 复核 should-fix：先作废 hook 代际与
          // store 在途——否则先读后提交流程的 prepareBaseline 已成功返回后，树/操作/标记
          // 在途时触发本分支，resetAll 后 Promise.all 恢复仍通过 token 校验复活已清空的主体）。
          // 文案用中性「当前主体」（跨入口残留 context 被清时按入口称呼会指称不准，评审 P3）
          ++matrixToken;
          matrixLoading.value = false;
          grantStore.cancelPending();
          grantStore.resetAll();
          activeKey.value = null;
          message("当前主体已不存在，请重新选择", {
            type: "warning"
          });
          break;
        case "none":
          break;
      }
    } finally {
      refreshInFlight.value = false;
    }
  }

  // ========== 授权弹窗（§4；触发：无权限单元格 / 工具栏授权按钮） ==========

  const dialogVisible = ref(false);
  const dialogInitial = ref<{
    operationCode?: string;
    resourceTypeCode?: string;
    resourceCode?: string | null;
    codeType?: string | null;
    scopeMode?: "INSTANCE" | "ALL";
  }>({});

  function openGrantDialog(initial: typeof dialogInitial.value = {}) {
    if (grantStore.capability !== "edit") {
      message("当前为只读视图，无授权权限", { type: "warning" });
      return;
    }
    if (frozen.value) {
      message("正在保存或切换主体，请稍后", { type: "warning" });
      return;
    }
    dialogInitial.value = initial;
    dialogVisible.value = true;
  }

  function handleDialogConfirm(changes: DraftChange[]) {
    grantStore.applyChanges(changes);
    dialogVisible.value = false;
  }

  // ========== 详情层（§5；触发：有权限单元格点击） ==========

  const drawerVisible = ref(false);
  /** 详情目标：单元格（资源行 × 操作列） */
  const drawerTarget = ref<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    resourceName: string;
    operationCode: string;
    scopeMode: "INSTANCE" | "ALL";
  } | null>(null);

  function openDetail(target: NonNullable<typeof drawerTarget.value>) {
    drawerTarget.value = target;
    drawerVisible.value = true;
  }

  /** 详情目标分组键下的全部生效记录（MANUAL + AUTO_DEP 并列，§3.3） */
  const drawerRecords = computed<EffectiveRecord[]>(() => {
    const target = drawerTarget.value;
    if (!target) return [];
    return effective.value.mains.filter(record => {
      if (record.resourceTypeCode !== target.resourceTypeCode) return false;
      if ((record.resourceCode ?? null) !== target.resourceCode) return false;
      if ((record.codeType ?? null) !== target.codeType) return false;
      if (record.scopeMode !== target.scopeMode) return false;
      // 操作列命中：operationCode 相等，或组合位记录该列位被覆盖（按位拆解，P1-4）
      if (record.operationCode === target.operationCode) return true;
      if (record.operationCode == null) {
        const merged = mergeOperationsForType(
          operationDefs.value,
          record.resourceTypeCode
        );
        const col = merged.find(c => c.code === target.operationCode);
        if (!col) return false;
        const { coveredSet } = coveredSetOf(record.grantedBits, merged);
        return (coveredSet & col.binaryBit) !== 0n;
      }
      return false;
    });
  });

  /** 子权限（详情层 Tab2）：按父记录 id 分组（含草稿虚拟挂载） */
  function childrenOf(record: EffectiveRecord): EffectiveRecord[] {
    if (record.draftMark === "add" && record.changeId) {
      return (
        effective.value.childrenByParent.get(draftParentKey(record.changeId)) ??
        []
      );
    }
    return (
      effective.value.childrenByParent.get(persistedParentKey(record.id)) ?? []
    );
  }

  // ========== 保存 / 放弃（§6.4 单入口） ==========

  async function handleSaveAll() {
    // 单类型矩阵：保存响应按当前类型过滤（filterBaselineByType，review P1-1）
    const ok = await grantStore.saveAll(currentTypeCode.value);
    if (ok) {
      // T-FE-038 review P2-2：保存成功后刷新"已授权类型"标记（subjectPermissionTypes 仅标记/排序用）——
      // 新增首条主权限 → 当前类型加入标记；删除最后一条 → 移出标记。
      // baseline 已按当前类型过滤（filterBaselineByType），主权限是否为空即类型是否有授权。
      const typeCode = currentTypeCode.value;
      if (typeCode != null) {
        const hasMain = grantStore.baseline.some(r => r.dependOn == null);
        const types = new Set(subjectPermissionTypes.value);
        if (hasMain) {
          types.add(typeCode);
        } else {
          types.delete(typeCode);
        }
        subjectPermissionTypes.value = [...types].sort();
      }
      message("保存成功", { type: "success" });
    } else if (grantStore.submit.kind === "saveFailed") {
      // 失败条目保留标红、整体重试；文案经错误码映射（DoD-1）
      message(grantStore.submit.message, { type: "error" });
    }
  }

  async function handleRevertAll() {
    if (!grantStore.isDirty) return;
    try {
      await ElMessageBox.confirm(
        `确认放弃全部 ${grantStore.changeCount} 条未保存变更？`,
        "放弃全部",
        { confirmButtonText: "放弃", cancelButtonText: "取消", type: "warning" }
      );
    } catch {
      return;
    }
    grantStore.revertAll();
  }

  // ========== 变更清单定位（§6.3：滚动矩阵到对应单元格并闪烁） ==========

  const locateRequest = ref<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    operationCode: string | null;
    ts: number;
  } | null>(null);

  /**
   * 变更定位（§6.3：滚动矩阵到对应单元格并闪烁）。
   * T-FE-038 review P2-4：子权限变更（含跨类型子权限）投影到父主权限单元格
   * （父一定在当前类型矩阵内）；主权限变更用自身定位。
   */
  function handleLocateChange(change: DraftChange) {
    const parent = parentLocateOf(
      change,
      grantStore.changes,
      effective.value.mains
    );
    const target = parent ?? {
      resourceTypeCode: change.summary.resourceTypeCode,
      resourceCode: change.summary.resourceCode,
      codeType: change.summary.codeType,
      operationCode: change.summary.operationCode
    };
    locateRequest.value = {
      ...target,
      ts: Date.now()
    };
  }

  // ========== 离开保护（§6.5：路由切换/刷新/切换主体 → 确认提示） ==========

  onBeforeRouteLeave(async () => {
    // saving 优先拦截（评审问题 1）：请求已发出不可放弃，禁止离开且不提供放弃选项
    if (grantStore.isSaving) {
      message("正在保存，禁止离开", { type: "warning" });
      return false;
    }
    // 未保存离开拦截（§6.5）：确认放弃 → 清空草稿放行（keepAlive 下组件不销毁，需显式回滚）
    if (!grantStore.isDirty) return true;
    const ok = await confirmDiscardIfDirty();
    if (ok) grantStore.revertAll();
    return ok;
  });

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    // dirty 或 saving 均拦截（评审问题 1：saving 期间刷新/关闭也禁止；returnValue 兼容浏览器）
    if (grantStore.isDirty || grantStore.isSaving) {
      event.preventDefault();
      event.returnValue = "";
    }
  }

  // ========== 生命周期 ==========

  onMounted(() => {
    loadDeps();
    window.addEventListener("beforeunload", handleBeforeUnload);
    // 首次加载树（onActivated 也会触发，single-flight 去重，问题 6）
    refreshAndPreset();
  });

  onActivated(() => {
    // keep-alive 重入刷新树（覆盖新建/改名/删除/层级/关联变化，问题 6）
    refreshAndPreset();
  });

  // 已激活时 query 更新（角色管理页跳转）：预选（问题 6；空 query 忽略）
  watch(
    () => route.query.roleExternalId,
    externalId => {
      if (typeof externalId !== "string" || !externalId) return;
      refreshAndPreset();
    },
    { flush: "post" }
  );

  onBeforeUnmount(() => {
    window.removeEventListener("beforeunload", handleBeforeUnload);
    // review P1-3：卸载必须先作废 hook 代际（++matrixToken）——resetAll 只能作废已启动的
    // store 请求；先读后提交的读阶段在途请求若未被代际作废，卸载后仍会通过 token 校验
    // 并 commitSubject 向全局 store 提交主体（甚至继续路由后处理）
    ++matrixToken;
    matrixLoading.value = false;
    grantStore.cancelPending();
    grantStore.resetAll();
  });

  return {
    // 主体/门控
    subjectType,
    canView,
    canManage,
    grantStore,
    // 依赖数据
    /** 全量资源森林（授权弹窗子权限配置器与详情层使用，子权限可跨类型） */
    allResourceForest,
    /** 全量操作定义（授权弹窗子权限配置器与详情层使用） */
    allOperationDefs,
    resourceForest,
    operationDefs,
    conditions,
    depsLoading,
    /** 🔧 T-FE-018：TYPE_DEFINITION:VIEW 软依赖降级态（true=类型下拉/矩阵区禁用+重试） */
    typePermDenied,
    retryLoadDeps,
    // 矩阵上下文（T-FE-038）
    typeCandidates,
    currentTypeCode,
    subjectPermissionTypes,
    matrixLoading,
    // 查看态
    includeResourceInherit,
    includeOpInherit,
    resourceKeyword,
    hiddenColumnCodes,
    // 派生
    effective,
    sourceChain,
    unionColumns,
    visibleColumns,
    // 主体选择
    handleSelectSubject,
    handleSwitchType,
    confirmDiscardIfDirty,
    subjectTreeRef,
    activeKey,
    selectingKey,
    groupHint,
    onSelectSubject,
    onSelectGroup,
    frozen,
    // 弹窗
    dialogVisible,
    dialogInitial,
    openGrantDialog,
    handleDialogConfirm,
    // 详情层
    drawerVisible,
    drawerTarget,
    drawerRecords,
    openDetail,
    childrenOf,
    // 保存/放弃
    handleSaveAll,
    handleRevertAll,
    // 定位
    locateRequest,
    handleLocateChange
  };
}
