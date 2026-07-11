import { ref, reactive, computed, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import { hasPerms } from "@/utils/auth";
import {
  getConditionList,
  createCondition,
  updateCondition,
  removeConditions,
  type ConditionResp
} from "@/api/permission-condition";
import { CONDITION_PERMS } from "./perms";
import { serializeRules, type ConditionFormData } from "./types";

/**
 * 权限条件页 hook。
 *
 * 布局：单表格（扁平条件模板 CRUD，非树）+ 弹窗表单（含条件规则可视化编辑器）。
 *
 * 权限（设计 §权限接线）：
 * - CONDITION:VIEW 门控列表加载与路由可达性（后端 list/detail 无校验，🔧 登记同 T-PERM-029）。
 * - CONDITION:CREATE/UPDATE/DELETE 门控写操作，三档独立（非 MANAGE，对齐后端）。
 * - loadList 内置 VIEW 短路，无权限直接清空返回。
 *
 * 范式对齐 type-def/utils/hook.ts（扁平表格 CRUD）。后端 list 无分页无筛选，
 * 前端本地过滤（keyword + enabled）。
 */
export function usePermissionCondition() {
  // ========== 权限门控 ==========
  const canView = computed(() => hasPerms(CONDITION_PERMS.CONDITION_VIEW));
  const canCreate = computed(() => hasPerms(CONDITION_PERMS.CONDITION_ADD));
  const canEdit = computed(() => hasPerms(CONDITION_PERMS.CONDITION_EDIT));
  const canDelete = computed(() => hasPerms(CONDITION_PERMS.CONDITION_DELETE));

  // ========== 列表 ==========
  const list = ref<ConditionResp[]>([]);
  const loading = ref(false);
  const search = reactive({
    keyword: "",
    enabled: "" as "" | "true" | "false"
  });

  async function loadList() {
    if (!canView.value) {
      list.value = [];
      return;
    }
    loading.value = true;
    try {
      const res = await getConditionList();
      list.value = res.items.slice().sort((a, b) => a.id - b.id);
    } catch (e: any) {
      message(e.message || "加载条件列表失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  const filteredList = computed(() => {
    return list.value.filter(c => {
      if (search.keyword) {
        const kw = search.keyword.toLowerCase();
        if (
          !c.code.toLowerCase().includes(kw) &&
          !c.name.toLowerCase().includes(kw)
        )
          return false;
      }
      if (search.enabled === "true" && !c.enabled) return false;
      if (search.enabled === "false" && c.enabled) return false;
      return true;
    });
  });

  function resetFilters() {
    search.keyword = "";
    search.enabled = "";
  }

  // ========== CRUD ==========
  async function submitCondition(
    form: ConditionFormData,
    mode: "create" | "edit",
    editingId?: number
  ): Promise<boolean> {
    try {
      const conditionRules = serializeRules(form.rules);
      if (mode === "create") {
        await createCondition({
          code: form.code,
          name: form.name,
          conditionRules,
          enabled: form.enabled,
          gatewayEvaluable: form.gatewayEvaluable,
          description: form.description || undefined
        });
        message("条件创建成功", { type: "success" });
      } else if (editingId) {
        await updateCondition({
          conditionId: editingId,
          name: form.name,
          conditionRules,
          enabled: form.enabled,
          gatewayEvaluable: form.gatewayEvaluable,
          description: form.description || undefined
        });
        message("条件更新成功", { type: "success" });
      }
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  async function deleteCondition(row: ConditionResp): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除条件「${row.name}（${row.code}）」吗？关联的授权关系将失效。`,
        "删除条件",
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
      await removeConditions([row.id]);
      message("条件已删除", { type: "success" });
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  onMounted(() => {
    loadList();
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
    submitCondition,
    deleteCondition
  };
}
