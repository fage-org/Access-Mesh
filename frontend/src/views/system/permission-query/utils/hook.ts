import { ref, reactive, computed } from "vue";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_QUERY_PERMS } from "./perms";
import {
  getEffectivePermissions,
  getQueryScopes,
  getExplain,
  type EffectivePermissionsReq,
  type EffectivePermissionsResp,
  type QueryScopesReq,
  type QueryScopesResp,
  type ExplainReq,
  type ExplainResp,
  type TargetType
} from "@/api/permission-query";

/**
 * 权限排查页 hook（三 Tab 独立查询 + 权限门控短路）。
 *
 * 设计要点：
 * 1. 权限门控短路：canQuery=false 时所有 load 直接 return，不发请求（路由框架不消费
 *    meta.auths 隐藏菜单，故 hook 层必须短路，配合 index.vue 整页无权状态）。
 * 2. 请求序号 reqSeq：每 Tab 独立，过期请求静默丢弃（旧请求后返回不覆盖新主体结果）。
 * 3. 主体变化清空旧结果：onReset/切换 targetType 时清空 result，避免旧权限事实误认为当前结果。
 * 4. 主体模型：Tab1/3 支持 targetType=USER/ROLE；Tab2 仅 USER（query-scopes 只解析用户）。
 * 5. 数组筛选字段（resourceTypeCodes/operationCodes 等）表单层用逗号分隔 string，
 *    load 时 split 为数组传 API，避免 el-input 绑定数组的类型冲突。
 *
 * 范式对齐 T-FE-012 reqSeq 机制（审计页优先保证不展示与当前筛选条件不符的数据）。
 */

/** 逗号分隔字符串拆为数组（trim + 过滤空） */
function splitCodes(input: string): string[] {
  if (!input) return [];
  return input
    .split(",")
    .map(s => s.trim())
    .filter(Boolean);
}

// ========== 公共：权限门控 + 三 Tab 汇总 ==========

export function usePermissionQuery() {
  /** 权限门控：临时复用 SYSTEM_CONFIG:VIEW（T-PERM-033 后切换 PERMISSION_QUERY:VIEW） */
  const canQuery = computed(() => hasPerms(PERMISSION_QUERY_PERMS.QUERY_VIEW));
  // 传 () => canQuery.value 避免 ComputedRef 与 () => boolean 类型冲突
  const tab1 = useEffectivePermissionsTab(() => canQuery.value);
  const tab2 = useQueryScopesTab(() => canQuery.value);
  const tab3 = useExplainTab(() => canQuery.value);
  return { canQuery, tab1, tab2, tab3 };
}

// ========== Tab1: effective-permissions（当前有效权限，USER/ROLE，分页） ==========

function useEffectivePermissionsTab(canQuery: () => boolean) {
  const form = reactive({
    targetType: "USER" as TargetType,
    // USER 分支
    subjectTypeCode: "ADMIN_USER",
    subjectExternalId: "",
    // ROLE 分支
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "",
    domainCode: "example",
    // 筛选（逗号分隔 string，load 时 split）
    resourceTypeCodes: "",
    operationCodes: "",
    resourceKeyword: "",
    includeSourceRoles: true,
    sourceRoleLimit: 3,
    // 分页
    pageNum: 1,
    pageSize: 10
  });
  const result = ref<EffectivePermissionsResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  async function load() {
    // 权限短路：无权不发请求
    if (!canQuery()) return;
    const seq = ++reqSeq;
    loading.value = true;
    try {
      const resourceTypeCodes = splitCodes(form.resourceTypeCodes);
      const operationCodes = splitCodes(form.operationCodes);
      const req: EffectivePermissionsReq = {
        targetType: form.targetType,
        subjectTypeCode:
          form.targetType === "USER" ? form.subjectTypeCode : undefined,
        subjectExternalId:
          form.targetType === "USER" ? form.subjectExternalId : undefined,
        roleTypeCode:
          form.targetType === "ROLE" ? form.roleTypeCode : undefined,
        roleExternalId:
          form.targetType === "ROLE" ? form.roleExternalId : undefined,
        domainCode: form.domainCode || undefined,
        resourceTypeCodes: resourceTypeCodes.length
          ? resourceTypeCodes
          : undefined,
        operationCodes: operationCodes.length ? operationCodes : undefined,
        resourceKeyword: form.resourceKeyword || undefined,
        includeSourceRoles: form.includeSourceRoles,
        sourceRoleLimit: form.sourceRoleLimit,
        pageNum: form.pageNum,
        pageSize: form.pageSize
      };
      const res = await getEffectivePermissions(req);
      // 过期请求静默丢弃
      if (seq !== reqSeq) return;
      result.value = res;
    } catch (e: any) {
      if (seq !== reqSeq) return;
      result.value = null;
      message(e.message || "查询有效权限失败", { type: "error" });
    } finally {
      if (seq === reqSeq) loading.value = false;
    }
  }

  function onSearch() {
    form.pageNum = 1;
    load();
  }

  function onReset() {
    form.targetType = "USER";
    form.subjectTypeCode = "ADMIN_USER";
    form.subjectExternalId = "";
    form.roleTypeCode = "BASIC_ROLE";
    form.roleExternalId = "";
    form.domainCode = "example";
    form.resourceTypeCodes = "";
    form.operationCodes = "";
    form.resourceKeyword = "";
    form.pageNum = 1;
    // 主体变化清空旧结果
    result.value = null;
  }

  function onPageChange(p: number) {
    form.pageNum = p;
    load();
  }

  function onPageSizeChange(s: number) {
    form.pageSize = s;
    form.pageNum = 1;
    load();
  }

  /** 切换 targetType 时清空旧结果 */
  function onTargetTypeChange() {
    result.value = null;
    form.pageNum = 1;
  }

  return {
    form,
    result,
    loading,
    load,
    onSearch,
    onReset,
    onPageChange,
    onPageSizeChange,
    onTargetTypeChange
  };
}

// ========== Tab2: query-scopes（范围权限四态，仅 USER） ==========

function useQueryScopesTab(canQuery: () => boolean) {
  const form = reactive({
    subjectTypeCode: "ADMIN_USER",
    subjectExternalId: "",
    domainCode: "example",
    // 主资源
    parentResourceTypeCode: "REPORT",
    parentResourceCode: "report:sales",
    parentCodeType: "default",
    // 逗号分隔 string
    parentOperationCodes: "DATA_READ,DATA_EDIT",
    scopeResourceTypeCodes: "DATA",
    scopeOperationCodes: "DATA_READ,DATA_EDIT,DATA_EXPORT,DATA_DELETE",
    scopeCodeType: "default"
  });
  const result = ref<QueryScopesResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  async function load() {
    if (!canQuery()) return;
    const seq = ++reqSeq;
    loading.value = true;
    try {
      const parentOps = splitCodes(form.parentOperationCodes);
      const scopeTypes = splitCodes(form.scopeResourceTypeCodes);
      const scopeOps = splitCodes(form.scopeOperationCodes);
      const req: QueryScopesReq = {
        subjectTypeCode: form.subjectTypeCode,
        subjectExternalId: form.subjectExternalId,
        domainCode: form.domainCode || undefined,
        parentResourceTypeCode: form.parentResourceTypeCode,
        parentResourceCode: form.parentResourceCode,
        parentCodeType: form.parentCodeType || undefined,
        parentOperationCodes: parentOps,
        scopeResourceTypeCodes: scopeTypes,
        scopeOperationCodes: scopeOps,
        scopeCodeType: form.scopeCodeType || undefined
      };
      const res = await getQueryScopes(req);
      if (seq !== reqSeq) return;
      result.value = res;
    } catch (e: any) {
      if (seq !== reqSeq) return;
      result.value = null;
      message(e.message || "查询范围权限失败", { type: "error" });
    } finally {
      if (seq === reqSeq) loading.value = false;
    }
  }

  function onSearch() {
    load();
  }

  function onReset() {
    form.subjectTypeCode = "ADMIN_USER";
    form.subjectExternalId = "";
    form.domainCode = "example";
    form.parentResourceTypeCode = "REPORT";
    form.parentResourceCode = "report:sales";
    form.parentCodeType = "default";
    form.parentOperationCodes = "DATA_READ,DATA_EDIT";
    form.scopeResourceTypeCodes = "DATA";
    form.scopeOperationCodes = "DATA_READ,DATA_EDIT,DATA_EXPORT,DATA_DELETE";
    form.scopeCodeType = "default";
    result.value = null;
  }

  return { form, result, loading, load, onSearch, onReset };
}

// ========== Tab3: explain（单权限解释，USER/ROLE） ==========

function useExplainTab(canQuery: () => boolean) {
  const form = reactive({
    targetType: "USER" as TargetType,
    // USER 分支
    subjectTypeCode: "ADMIN_USER",
    subjectExternalId: "",
    // ROLE 分支
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "",
    domainCode: "example",
    // 目标权限
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE" as "INSTANCE" | "ALL",
    includeSourceRoles: true,
    includeRecentChanges: true,
    recentDays: 30
  });
  const result = ref<ExplainResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  async function load() {
    if (!canQuery()) return;
    const seq = ++reqSeq;
    loading.value = true;
    try {
      const isAll = form.scopeMode === "ALL";
      const req: ExplainReq = {
        targetType: form.targetType,
        subjectTypeCode:
          form.targetType === "USER" ? form.subjectTypeCode : undefined,
        subjectExternalId:
          form.targetType === "USER" ? form.subjectExternalId : undefined,
        roleTypeCode:
          form.targetType === "ROLE" ? form.roleTypeCode : undefined,
        roleExternalId:
          form.targetType === "ROLE" ? form.roleExternalId : undefined,
        domainCode: form.domainCode || undefined,
        resourceTypeCode: form.resourceTypeCode,
        // scopeMode=ALL 时不传 resourceCode/codeType
        resourceCode: isAll ? undefined : form.resourceCode,
        codeType: isAll ? undefined : form.codeType,
        operationCode: form.operationCode,
        scopeMode: form.scopeMode,
        includeSourceRoles: form.includeSourceRoles,
        includeRecentChanges: form.includeRecentChanges,
        recentDays: form.recentDays
      };
      const res = await getExplain(req);
      if (seq !== reqSeq) return;
      result.value = res;
    } catch (e: any) {
      if (seq !== reqSeq) return;
      result.value = null;
      message(e.message || "权限解释失败", { type: "error" });
    } finally {
      if (seq === reqSeq) loading.value = false;
    }
  }

  function onSearch() {
    load();
  }

  function onReset() {
    form.targetType = "USER";
    form.subjectTypeCode = "ADMIN_USER";
    form.subjectExternalId = "";
    form.roleTypeCode = "BASIC_ROLE";
    form.roleExternalId = "";
    form.domainCode = "example";
    form.resourceTypeCode = "REPORT";
    form.resourceCode = "report:sales";
    form.codeType = "default";
    form.operationCode = "DATA_READ";
    form.scopeMode = "INSTANCE";
    form.includeSourceRoles = true;
    form.includeRecentChanges = true;
    form.recentDays = 30;
    result.value = null;
  }

  /** 切换 targetType 清空旧结果 */
  function onTargetTypeChange() {
    result.value = null;
  }

  return { form, result, loading, load, onSearch, onReset, onTargetTypeChange };
}
