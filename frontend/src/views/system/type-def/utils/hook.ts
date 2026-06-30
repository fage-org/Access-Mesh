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
 * 后端 list 现仅 domainCode（未生效），mock 按 typeKey/keyword 本地过滤（登记 T-PERM-023 🔧）。
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
      // 后端 /list 返回 ItemsResp（全量，无分页/无 typeKey 过滤，登记 T-PERM-023 🔧）。
      // 前端本地做 typeKey/keyword 过滤 + sortOrder 排序 + 切片分页。字典表量小，每次翻页重拉全量可接受。
      const res = await getTypeDefList({});
      let all = res.items.slice();
      if (searchForm.typeKey) {
        all = all.filter(t => t.typeKey === searchForm.typeKey);
      }
      if (searchForm.keyword) {
        const kw = searchForm.keyword.toLowerCase();
        all = all.filter(
          t =>
            t.name.toLowerCase().includes(kw) ||
            t.typeCode.toLowerCase().includes(kw)
        );
      }
      all.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
      pagination.total = all.length;
      const start = (pagination.page - 1) * pagination.size;
      tableData.value = all.slice(start, start + pagination.size);
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
          extra: form.extra || null
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
