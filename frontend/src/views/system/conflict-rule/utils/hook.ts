import { ref, reactive, computed, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import { hasPerms } from "@/utils/auth";
import {
  getConflictRuleList,
  createConflictRule,
  updateConflictRule,
  removeConflictRules,
  detectConflictRule,
  type ConflictRuleResp,
  type ConflictDetectResp,
  type ConflictDetectReq,
  type ConflictRuleCreateReq
} from "@/api/conflict-rule";
import { getRoleList, type RoleResp } from "@/api/role-manage";
import {
  getOperationList,
  type OperationPermissionResp
} from "@/api/resource-operation";
import { getTypeDefList, type TypeDefResp, TYPE_KEY } from "@/api/type-def";
import { CONFLICT_RULE_PERMS } from "./perms";
import type { ConflictFormData } from "./types";

/**
 * 冲突规则页 hook。
 *
 * 布局：单表格（扁平冲突规则 CRUD）+ 表单弹窗 + 检测对话框。
 *
 * 权限（设计 §权限接线）：
 * - CONFLICT_RULE:VIEW 门控列表加载与路由可达性（后端 list/detail 无校验，🔧 登记同 T-PERM-030）。
 * - CONFLICT_RULE:CREATE/UPDATE/DELETE 门控写操作，三档独立（非 MANAGE，对齐后端）。
 * - loadList 内置 VIEW 短路，无权限直接清空返回。
 *
 * 引用数据（设计 §Q1/Q3）：页面加载时并行请求角色列表（BASIC_ROLE+GROUP_ROLE）、
 * 操作权限列表、类型定义列表（筛 resource_type），建立 id->name 映射供表格展示与表单选择器。
 * 引用数据加载失败不阻塞主列表（名称回退显示 #ID）。
 *
 * 范式对齐 permission-condition/utils/hook.ts（扁平表格 CRUD）。后端 list 无分页无筛选，
 * 前端本地过滤（keyword + conflictType）。
 */
export function useConflictRule() {
  // ========== 权限门控 ==========
  const canView = computed(() =>
    hasPerms(CONFLICT_RULE_PERMS.CONFLICT_RULE_VIEW)
  );
  const canCreate = computed(() =>
    hasPerms(CONFLICT_RULE_PERMS.CONFLICT_RULE_ADD)
  );
  const canEdit = computed(() =>
    hasPerms(CONFLICT_RULE_PERMS.CONFLICT_RULE_EDIT)
  );
  const canDelete = computed(() =>
    hasPerms(CONFLICT_RULE_PERMS.CONFLICT_RULE_DELETE)
  );

  // ========== 列表 ==========
  const list = ref<ConflictRuleResp[]>([]);
  const loading = ref(false);
  const search = reactive({
    keyword: "",
    conflictType: "" as "" | "ROLE_MUTEX" | "PERM_MUTEX"
  });

  // ========== 引用数据映射 ==========
  const roleMap = ref<Map<number, RoleResp>>(new Map());
  const operationMap = ref<Map<number, OperationPermissionResp>>(new Map());
  const resourceTypeMap = ref<Map<number, TypeDefResp>>(new Map());

  /** 角色选项（BASIC_ROLE + GROUP_ROLE，表单选择器用） */
  const roleOptions = computed(() =>
    Array.from(roleMap.value.values()).sort((a, b) => a.sortOrder - b.sortOrder)
  );
  /** 操作权限选项（表单/检测对话框选择器用） */
  const operationOptions = computed(() =>
    Array.from(operationMap.value.values()).sort((a, b) => a.id - b.id)
  );
  /** 资源类型选项（type_definition type_key=resource_type，表单选择器用） */
  const resourceTypeOptions = computed(() =>
    Array.from(resourceTypeMap.value.values()).sort(
      (a, b) => a.sortOrder - b.sortOrder
    )
  );

  async function loadRefData() {
    try {
      const [roleRes, opRes, typeRes] = await Promise.all([
        getRoleList({
          roleTypeCodes: ["BASIC_ROLE", "GROUP_ROLE"],
          pageSize: 200
        }),
        getOperationList({}),
        getTypeDefList({})
      ]);
      roleMap.value = new Map(roleRes.items.map(r => [r.id, r]));
      operationMap.value = new Map(opRes.items.map(o => [o.id, o]));
      resourceTypeMap.value = new Map(
        typeRes.items
          .filter(t => t.typeKey === TYPE_KEY.RESOURCE_TYPE)
          .map(t => [t.typeValue, t])
      );
    } catch (e: any) {
      // 映射加载失败不阻塞主列表（名称将回退显示 #ID）
      console.warn("[conflict-rule] 加载引用数据失败:", e?.message);
    }
  }

  async function loadList() {
    if (!canView.value) {
      list.value = [];
      return;
    }
    loading.value = true;
    try {
      const res = await getConflictRuleList();
      list.value = res.items.slice().sort((a, b) => a.id - b.id);
    } catch (e: any) {
      message(e.message || "加载冲突规则列表失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  const filteredList = computed(() => {
    return list.value.filter(r => {
      if (search.conflictType && r.conflictType !== search.conflictType)
        return false;
      if (search.keyword) {
        const kw = search.keyword.toLowerCase();
        const first = resolveFirstName(r).toLowerCase();
        const second = resolveSecondName(r).toLowerCase();
        const desc = (r.description ?? "").toLowerCase();
        if (!first.includes(kw) && !second.includes(kw) && !desc.includes(kw))
          return false;
      }
      return true;
    });
  });

  function resetFilters() {
    search.keyword = "";
    search.conflictType = "";
  }

  // ========== 名称解析（表格展示用，映射缺失回退 #ID） ==========
  function resolveFirstName(r: ConflictRuleResp): string {
    if (r.conflictType === "ROLE_MUTEX") {
      return resolveRoleName(r.firstAbstractRoleId);
    }
    return resolveOperationName(r.firstOperationPermissionId);
  }

  function resolveSecondName(r: ConflictRuleResp): string {
    if (r.conflictType === "ROLE_MUTEX") {
      return resolveRoleName(r.secondAbstractRoleId);
    }
    return resolveOperationName(r.secondOperationPermissionId);
  }

  function resolveRoleName(id: number | null): string {
    if (id == null) return "-";
    const role = roleMap.value.get(id);
    return role ? role.name : `#${id}`;
  }

  function resolveOperationName(id: number | null): string {
    if (id == null) return "-";
    const op = operationMap.value.get(id);
    return op ? op.name : `#${id}`;
  }

  function resolveResourceTypeName(r: ConflictRuleResp): string {
    if (r.resourceTypeValue == null) return "全部";
    const t = resourceTypeMap.value.get(r.resourceTypeValue);
    return t ? t.name : `#${r.resourceTypeValue}`;
  }

  // ========== CRUD ==========
  function buildPayload(form: ConflictFormData): ConflictRuleCreateReq {
    if (form.conflictType === "ROLE_MUTEX") {
      return {
        conflictType: form.conflictType,
        firstAbstractRoleId: form.firstAbstractRoleId,
        secondAbstractRoleId: form.secondAbstractRoleId,
        firstOperationPermissionId: null,
        secondOperationPermissionId: null,
        resourceTypeValue: null,
        description: form.description
      };
    }
    return {
      conflictType: form.conflictType,
      firstAbstractRoleId: null,
      secondAbstractRoleId: null,
      firstOperationPermissionId: form.firstOperationPermissionId,
      secondOperationPermissionId: form.secondOperationPermissionId,
      resourceTypeValue: form.resourceTypeValue,
      description: form.description
    };
  }

  async function submitConflictRule(
    form: ConflictFormData,
    mode: "create" | "edit",
    editingId?: number
  ): Promise<boolean> {
    try {
      const payload = buildPayload(form);
      if (mode === "create") {
        await createConflictRule(payload);
        message("冲突规则创建成功", { type: "success" });
      } else if (editingId) {
        await updateConflictRule({ id: editingId, ...payload });
        message("冲突规则更新成功", { type: "success" });
      }
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  async function deleteConflictRule(row: ConflictRuleResp): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除冲突规则「${resolveFirstName(row)} ↔ ${resolveSecondName(row)}」吗？`,
        "删除冲突规则",
        {
          type: "warning",
          confirmButtonText: "删除",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return false;
    }
    try {
      await removeConflictRules([row.id]);
      message("冲突规则已删除", { type: "success" });
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  // ========== detect ==========
  async function runDetect(
    req: ConflictDetectReq
  ): Promise<ConflictDetectResp | null> {
    try {
      return await detectConflictRule(req);
    } catch (e: any) {
      message(e.message || "冲突检测失败", { type: "error" });
      return null;
    }
  }

  onMounted(() => {
    loadList();
    loadRefData();
  });

  return {
    canView,
    canCreate,
    canEdit,
    canDelete,
    list,
    loading,
    search,
    filteredList,
    resetFilters,
    loadList,
    submitConflictRule,
    deleteConflictRule,
    runDetect,
    roleOptions,
    operationOptions,
    resourceTypeOptions,
    resolveFirstName,
    resolveSecondName,
    resolveResourceTypeName
  };
}
