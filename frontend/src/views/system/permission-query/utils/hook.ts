import { ref, reactive, computed, watch } from "vue";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_QUERY_PERMS } from "./perms";
import { ROLE_TYPE_OPTIONS } from "./types";
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
 * 权限排查页 hook（三 Tab 独立查询 + 权限门控短路 + 主体变化失效）。
 *
 * 设计要点：
 * 1. 权限门控短路：canQuery=false 时所有 load 直接 return，不发请求。
 * 2. 请求序号 reqSeq：每 Tab 独立，过期请求静默丢弃。
 * 3. invalidate()：递增 reqSeq + 清空 result + 复位 loading。主体业务键变化、重置、
 *    切换 targetType 时调用，确保在途请求回写时不覆盖新主体结果（评审 P1 修复）。
 * 4. watch 主体字段：用户编辑主体输入时自动 invalidate（无需点重置）。
 * 5. load 前 validate：必填字段校验（ORG/POSITION domainCode、externalId、INSTANCE
 *    resourceCode/codeType 等），校验失败 message 提示 + return（评审 P2 修复）。
 * 6. 主体模型：Tab1/3 支持 targetType=USER/ROLE；Tab2 仅 USER。
 * 7. 数组筛选字段表单层用逗号分隔 string，load 时 split 为数组。
 */

/** 逗号分隔字符串拆为数组（trim + 过滤空） */
function splitCodes(input: string): string[] {
  if (!input) return [];
  return input
    .split(",")
    .map(s => s.trim())
    .filter(Boolean);
}

/** 角色类型是否要求 domainCode 必填（ORG/POSITION） */
function isDomainRequired(roleTypeCode: string): boolean {
  const rt = ROLE_TYPE_OPTIONS.find(r => r.value === roleTypeCode);
  return rt?.domainRequired ?? false;
}

// ========== 公共：权限门控 + 三 Tab 汇总 ==========

export function usePermissionQuery() {
  // 页面级 UI 门 = 任一目标类型可查（T-PERM-033 设计定案：无独立排查码，
  // API 层按被查目标实例 USER:VIEW/ROLE:VIEW 逐一校验）
  const canQuery = computed(
    () =>
      hasPerms(PERMISSION_QUERY_PERMS.USER_VIEW) ||
      hasPerms(PERMISSION_QUERY_PERMS.ROLE_VIEW)
  );
  const tab1 = useEffectivePermissionsTab(() => canQuery.value);
  const tab2 = useQueryScopesTab(() => canQuery.value);
  const tab3 = useExplainTab(() => canQuery.value);
  return { canQuery, tab1, tab2, tab3 };
}

// ========== Tab1: effective-permissions（当前有效权限，USER/ROLE，分页） ==========

function useEffectivePermissionsTab(canQuery: () => boolean) {
  const form = reactive({
    targetType: "USER" as TargetType,
    subjectTypeCode: "LOCAL_USER",
    subjectExternalId: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "",
    domainCode: "example",
    resourceTypeCodes: "",
    operationCodes: "",
    resourceKeyword: "",
    includeSourceRoles: true,
    sourceRoleLimit: 3,
    pageNum: 1,
    pageSize: 10
  });
  const result = ref<EffectivePermissionsResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  /** 失效：递增 reqSeq 丢弃在途请求 + 清空旧结果 + 复位 loading */
  function invalidate() {
    reqSeq++;
    result.value = null;
    loading.value = false;
  }

  /** 主体业务键变化时失效（避免在途请求回写不一致的权限事实） */
  watch(
    () => [
      form.targetType,
      form.subjectTypeCode,
      form.subjectExternalId,
      form.roleTypeCode,
      form.roleExternalId,
      form.domainCode
    ],
    () => invalidate()
  );

  /** 提交前校验：返回错误消息（null=通过） */
  function validate(): string | null {
    if (form.targetType === "USER") {
      if (!form.subjectExternalId.trim()) return "请输入用户标识";
    } else {
      if (!form.roleExternalId.trim()) return "请输入角色标识";
      if (isDomainRequired(form.roleTypeCode) && !form.domainCode.trim()) {
        return `角色类型 ${form.roleTypeCode} 要求填写业务域`;
      }
    }
    return null;
  }

  async function load() {
    if (!canQuery()) return;
    const err = validate();
    if (err) {
      message(err, { type: "warning" });
      return;
    }
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
    form.subjectTypeCode = "LOCAL_USER";
    form.subjectExternalId = "";
    form.roleTypeCode = "BASIC_ROLE";
    form.roleExternalId = "";
    form.domainCode = "example";
    form.resourceTypeCodes = "";
    form.operationCodes = "";
    form.resourceKeyword = "";
    form.pageNum = 1;
    invalidate();
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

  function onTargetTypeChange() {
    invalidate();
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
    onTargetTypeChange,
    invalidate
  };
}

// ========== Tab2: query-scopes（范围权限四态，仅 USER） ==========

function useQueryScopesTab(canQuery: () => boolean) {
  // 示例默认值已清空（T-FE-019 设计定案）：mock 时代 REPORT/DATA 等示例在真实库
  // 不存在，默认值直接查询必得 DENIED/空态，对排查者是误导；编码写法由输入框
  // placeholder 引导
  const form = reactive({
    subjectTypeCode: "LOCAL_USER",
    subjectExternalId: "",
    domainCode: "example",
    parentResourceTypeCode: "",
    parentResourceCode: "",
    parentCodeType: "",
    parentOperationCodes: "",
    scopeResourceTypeCodes: "",
    scopeOperationCodes: "",
    scopeCodeType: ""
  });
  const result = ref<QueryScopesResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  function invalidate() {
    reqSeq++;
    result.value = null;
    loading.value = false;
  }

  /** 主体业务键变化时失效 */
  watch(
    () => [form.subjectTypeCode, form.subjectExternalId, form.domainCode],
    () => invalidate()
  );

  function validate(): string | null {
    if (!form.subjectExternalId.trim()) return "请输入用户标识";
    if (!form.parentResourceTypeCode.trim()) return "请输入主资源类型";
    if (!form.parentResourceCode.trim()) return "请输入主资源编码";
    if (!splitCodes(form.parentOperationCodes).length)
      return "请输入至少一个主操作";
    if (!splitCodes(form.scopeResourceTypeCodes).length)
      return "请输入至少一个范围资源类型";
    if (!splitCodes(form.scopeOperationCodes).length)
      return "请输入至少一个范围操作";
    return null;
  }

  async function load() {
    if (!canQuery()) return;
    const err = validate();
    if (err) {
      message(err, { type: "warning" });
      return;
    }
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
    form.subjectTypeCode = "LOCAL_USER";
    form.subjectExternalId = "";
    form.domainCode = "example";
    form.parentResourceTypeCode = "";
    form.parentResourceCode = "";
    form.parentCodeType = "";
    form.parentOperationCodes = "";
    form.scopeResourceTypeCodes = "";
    form.scopeOperationCodes = "";
    form.scopeCodeType = "";
    invalidate();
  }

  return { form, result, loading, load, onSearch, onReset, invalidate };
}

// ========== Tab3: explain（单权限解释，USER/ROLE） ==========

function useExplainTab(canQuery: () => boolean) {
  const form = reactive({
    targetType: "USER" as TargetType,
    subjectTypeCode: "LOCAL_USER",
    subjectExternalId: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "",
    domainCode: "example",
    // 示例默认值已清空（T-FE-019 设计定案，同 Tab2 注记）
    resourceTypeCode: "",
    resourceCode: "",
    codeType: "",
    operationCode: "",
    scopeMode: "INSTANCE" as "INSTANCE" | "ALL",
    /** 模拟客户端 IP（T-PERM-033 explain 扩展）：可选，IP 类条件按此评估；
     *  不填时后端回退当前请求 IP，响应 evaluationContextSource 标注来源 */
    clientIp: "",
    includeSourceRoles: true,
    includeRecentChanges: true,
    recentDays: 30
  });
  const result = ref<ExplainResp | null>(null);
  const loading = ref(false);
  let reqSeq = 0;

  function invalidate() {
    reqSeq++;
    result.value = null;
    loading.value = false;
  }

  watch(
    () => [
      form.targetType,
      form.subjectTypeCode,
      form.subjectExternalId,
      form.roleTypeCode,
      form.roleExternalId,
      form.domainCode,
      form.clientIp
    ],
    () => invalidate()
  );

  function validate(): string | null {
    if (form.targetType === "USER") {
      if (!form.subjectExternalId.trim()) return "请输入用户标识";
    } else {
      if (!form.roleExternalId.trim()) return "请输入角色标识";
      if (isDomainRequired(form.roleTypeCode) && !form.domainCode.trim()) {
        return `角色类型 ${form.roleTypeCode} 要求填写业务域`;
      }
    }
    if (!form.resourceTypeCode.trim()) return "请输入资源类型";
    if (!form.operationCode.trim()) return "请输入操作码";
    // INSTANCE 模式要求 resourceCode + codeType（ALL 模式不传）
    if (form.scopeMode === "INSTANCE") {
      if (!form.resourceCode.trim()) return "INSTANCE 模式要求资源编码";
      if (!form.codeType.trim()) return "INSTANCE 模式要求编码类型";
    }
    return null;
  }

  async function load() {
    if (!canQuery()) return;
    const err = validate();
    if (err) {
      message(err, { type: "warning" });
      return;
    }
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
        resourceCode: isAll ? undefined : form.resourceCode,
        codeType: isAll ? undefined : form.codeType,
        operationCode: form.operationCode,
        scopeMode: form.scopeMode,
        context: form.clientIp.trim()
          ? { clientIp: form.clientIp.trim() }
          : undefined,
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
    form.subjectTypeCode = "LOCAL_USER";
    form.subjectExternalId = "";
    form.roleTypeCode = "BASIC_ROLE";
    form.roleExternalId = "";
    form.domainCode = "example";
    form.resourceTypeCode = "";
    form.resourceCode = "";
    form.codeType = "";
    form.operationCode = "";
    form.scopeMode = "INSTANCE";
    form.clientIp = "";
    form.includeSourceRoles = true;
    form.includeRecentChanges = true;
    form.recentDays = 30;
    invalidate();
  }

  function onTargetTypeChange() {
    invalidate();
  }

  return {
    form,
    result,
    loading,
    load,
    onSearch,
    onReset,
    onTargetTypeChange,
    invalidate
  };
}
