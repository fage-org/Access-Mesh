import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getSystemConfigList,
  saveSystemConfig,
  type SystemConfigResp
} from "@/api/system-config";
import {
  createEmptyConfigForm,
  parseConfigValue,
  type SystemConfigFormData
} from "./types";

/**
 * 系统配置页 hook（分页表格 + save upsert）。
 *
 * 范式对齐 type-def/utils/hook.ts：tableData/loading/searchForm/pagination + loadTable/分页回调。
 * 后端 list 接 EmptyReq 无参，返回租户全量 ItemsResp（无 keyword 过滤，登记 T-PERM-024 🔧），
 * 前端本地做 keyword 过滤 + 切片分页。
 *
 * 后端仅 list/detail/save 三端点，save 为 upsert 幂等——新建/编辑统一走 saveSystemConfig，
 * 无 create/update/remove 调用，无 handleDelete。
 */
export function useSystemConfig() {
  const tableData = ref<SystemConfigResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive({
    keyword: ""
  });
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  async function loadTable() {
    loading.value = true;
    try {
      // 后端 /list 返回 ItemsResp（全量，无分页/无 keyword 过滤，登记 T-PERM-024 🔧）。
      // 前端本地做 keyword 过滤 + 按 configKey 排序 + 切片分页。配置项量小，每次翻页重拉全量可接受。
      const res = await getSystemConfigList({});
      let all = res.items.slice();
      if (searchForm.keyword) {
        const kw = searchForm.keyword.toLowerCase();
        all = all.filter(
          c =>
            c.configKey.toLowerCase().includes(kw) ||
            (c.description ?? "").toLowerCase().includes(kw)
        );
      }
      all.sort((a, b) => a.configKey.localeCompare(b.configKey));
      pagination.total = all.length;
      const start = (pagination.page - 1) * pagination.size;
      tableData.value = all.slice(start, start + pagination.size);
    } catch (e: any) {
      message(e.message || "加载系统配置失败", { type: "error" });
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

  /** 提交保存（新建/编辑统一 upsert）。
   *  configValue 提交前 JSON.parse 校验，非法则 message 报错返回 false 不提交（与后端 JsonValidationUtils 对齐）。 */
  async function handleSubmitForm(
    form: SystemConfigFormData
  ): Promise<boolean> {
    const parsed = parseConfigValue(form.configValue);
    if (!parsed.ok) {
      message(parsed.error || "配置值必须是合法 JSON", { type: "error" });
      return false;
    }
    try {
      await saveSystemConfig({
        configKey: form.configKey,
        configValue: form.configValue.trim(),
        description: form.description || null
      });
      message("保存成功", { type: "success" });
      await loadTable();
      return true;
    } catch (e: any) {
      message(e.message || "保存失败", { type: "error" });
      return false;
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
    createEmptyForm: createEmptyConfigForm
  };
}
