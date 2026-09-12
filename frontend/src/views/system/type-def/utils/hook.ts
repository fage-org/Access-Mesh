import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import {
  getTypeDefList,
  createTypeDef,
  updateTypeDef,
  removeTypeDefs,
  type TypeDefResp
} from "@/api/type-def";
import { createEmptyTypeDefForm, type TypeDefFormData } from "./types";

/**
 * 类型定义页 hook（分页表格 + CRUD）。
 *
 * 范式对齐 user/utils/hook.ts：tableData/loading/searchForm/pagination + loadTable/分页回调。
 * T-PERM-023 收口：list 服务端 typeKey/keyword 过滤 + 分页（ORDER BY sortOrder,id），前端只消费。
 */
export function useTypeDef() {
  const tableData = ref<TypeDefResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive({
    keyword: "",
    typeKey: null as string | null
  });
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  async function loadTable() {
    loading.value = true;
    try {
      const res = await getTypeDefList({
        typeKey: searchForm.typeKey ?? undefined,
        keyword: searchForm.keyword || undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      });
      tableData.value = res.items;
      pagination.total = res.total;
    } catch (e: any) {
      message(e.message || "加载类型定义失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.keyword = "";
    searchForm.typeKey = null;
    pagination.page = 1;
    loadTable();
  }

  function onPageChange(page: number) {
    pagination.page = page;
    loadTable();
  }

  function onPageSizeChange(size: number) {
    pagination.size = size;
    pagination.page = 1;
    loadTable();
  }

  /** 提交新建/编辑 */
  async function handleSubmitForm(
    mode: "create" | "edit",
    form: TypeDefFormData,
    editingId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        await createTypeDef({
          typeKey: form.typeKey,
          typeCode: form.typeCode || undefined,
          name: form.name,
          description: form.description || null,
          sortOrder: form.sortOrder,
          extra: form.extra || null,
          // T-PERM-062：resource_type 所有者角色（选择器值；空=后端缺省引导角色），
          // 选项固定 BASIC_ROLE 功能角色；非 resource_type 后端忽略该字段
          ownerRoleTypeCode: form.ownerRoleExternalId
            ? "BASIC_ROLE"
            : undefined,
          ownerRoleExternalId: form.ownerRoleExternalId || undefined
        });
        message("创建成功", { type: "success" });
      } else if (editingId) {
        await updateTypeDef({
          typeId: editingId,
          name: form.name,
          description: form.description || null,
          sortOrder: form.sortOrder,
          extra: form.extra || null
        });
        message("更新成功", { type: "success" });
      }
      await loadTable();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  /** 删除类型定义（系统预置项前端隐藏按钮，后端亦跳过） */
  async function handleDelete(row: TypeDefResp) {
    try {
      await ElMessageBox.confirm(
        `确认删除类型「${row.name}」？删除后该类型的 code/value 映射将失效。`,
        "删除确认",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      return;
    }
    try {
      await removeTypeDefs([row.id]);
      message("删除成功", { type: "success" });
      await loadTable();
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
    }
  }

  onMounted(() => {
    loadTable();
  });

  return {
    tableData,
    loading,
    searchForm,
    pagination,
    loadTable,
    onSearch,
    onReset,
    onPageChange,
    onPageSizeChange,
    handleSubmitForm,
    handleDelete,
    createEmptyForm: createEmptyTypeDefForm
  };
}
