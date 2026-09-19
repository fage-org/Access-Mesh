import { reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import { usePagedList } from "@/utils/list-load";
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
 * T-PERM-024 收口：list 服务端 keyword 过滤（configKey/description）+ 分页（ORDER BY configKey,id），
 * 前端只消费。T-FE-051：列表加载接线 usePagedList（失败提示保留旧数据 + latest-wins 请求代际）。
 *
 * 后端仅 list/detail/save 三端点，save 为 upsert 幂等——新建/编辑统一走 saveSystemConfig，
 * 无 create/update/remove 调用，无 handleDelete。
 */
export function useSystemConfig() {
  const searchForm = reactive({
    keyword: ""
  });

  const {
    tableData,
    loading,
    pagination,
    loadTable,
    onSearch,
    onPageChange,
    onPageSizeChange
  } = usePagedList<SystemConfigResp>({
    errorText: "加载系统配置失败",
    fetcher: (page, size) =>
      getSystemConfigList({
        keyword: searchForm.keyword || undefined,
        pageNum: page,
        pageSize: size
      })
  });

  function onReset() {
    searchForm.keyword = "";
    onSearch();
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
    } catch (e) {
      message(toErrorMessage(e, "保存失败"), { type: "error" });
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
