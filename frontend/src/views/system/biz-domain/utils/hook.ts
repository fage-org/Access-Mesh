import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getBizDomainList,
  createBizDomain,
  updateBizDomain,
  removeBizDomain,
  type BizDomainResp
} from "@/api/biz-domain";
import {
  getDomainConfigList,
  saveDomainConfig,
  removeDomainConfig,
  type DomainConfigResp
} from "@/api/domain-config";
import {
  createEmptyBizDomainForm,
  createEmptyDomainConfigForm,
  parseExtra,
  type BizDomainFormData,
  type DomainConfigFormData
} from "./types";

/**
 * 业务域页 hook（主从表格）。
 *
 * 范式对齐 system-config/utils/hook.ts，但本页为**主从布局**：
 * - 主表 BizDomain：分页表格 + CRUD（独立 create/update/remove，非 system-config 的纯 save upsert）。
 * - 子表 DomainConfig：选中域后加载该域配置列表（save upsert + remove，同 system-config 范式）。
 *
 * 后端 biz-domain list 接 EmptyReq 返回全量 ItemsResp（无分页，登记 T-PERM-026 🔧），
 * 前端本地做 keyword 过滤 + 切片分页。
 *
 * 主表 CRUD 区别于 system-config：biz-domain 有独立 create/update/remove 接口，
 * 故 handleSubmitBizDomain 区分 create/edit + handleDeleteBizDomain。
 */
export function useBizDomain() {
  // ========== 主表：BizDomain ==========
  const tableData = ref<BizDomainResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive({ keyword: "" });
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  // 当前选中的业务域（驱动子表加载）
  const currentDomain = ref<BizDomainResp | null>(null);

  // ========== 子表：DomainConfig ==========
  const configData = ref<DomainConfigResp[]>([]);
  const configLoading = ref(false);

  async function loadTable() {
    loading.value = true;
    try {
      // 后端 /list 返回 ItemsResp（全量，无分页/无过滤，登记 T-PERM-026 🔧）。
      // 前端本地做 keyword 过滤 + 按 code 排序 + 切片分页。业务域量小，每次翻页重拉全量可接受。
      const res = await getBizDomainList();
      let all = res.items.slice();
      if (searchForm.keyword) {
        const kw = searchForm.keyword.toLowerCase();
        all = all.filter(
          d =>
            d.code.toLowerCase().includes(kw) ||
            d.name.toLowerCase().includes(kw) ||
            (d.description ?? "").toLowerCase().includes(kw)
        );
      }
      all.sort((a, b) => a.code.localeCompare(b.code));
      pagination.total = all.length;
      const start = (pagination.page - 1) * pagination.size;
      tableData.value = all.slice(start, start + pagination.size);
    } catch (e: any) {
      message(e.message || "加载业务域失败", { type: "error" });
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

  /** 选中业务域 → 加载该域的 DomainConfig 子表 */
  function selectDomain(row: BizDomainResp) {
    currentDomain.value = row;
    loadConfigs();
  }

  /** 加载当前选中域的配置列表 */
  async function loadConfigs() {
    if (!currentDomain.value) {
      configData.value = [];
      return;
    }
    configLoading.value = true;
    try {
      const res = await getDomainConfigList(currentDomain.value.code);
      configData.value = res.items;
    } catch (e: any) {
      message(e.message || "加载域配置失败", { type: "error" });
      configData.value = [];
    } finally {
      configLoading.value = false;
    }
  }

  /** 提交业务域表单（create/edit 区分——biz-domain 有独立 create/update 接口）。
   *  edit 态需 domainId（🔧 后端用内部主键，应切业务键 code，登记 T-PERM-026）。
   *  返回 true 表示提交成功（弹窗可关闭）。 */
  async function handleSubmitBizDomain(
    mode: "create" | "edit",
    form: BizDomainFormData,
    domainId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        await createBizDomain({
          code: form.code,
          name: form.name,
          description: form.description || null
        });
        message("创建成功", { type: "success" });
      } else {
        if (domainId == null) {
          message("缺少业务域 ID", { type: "error" });
          return false;
        }
        await updateBizDomain({
          domainId,
          name: form.name || null,
          description: form.description || null
        });
        message("保存成功", { type: "success" });
        // 若编辑的是当前选中域，同步刷新标题
        if (currentDomain.value?.id === domainId) {
          currentDomain.value = { ...currentDomain.value, name: form.name };
        }
      }
      await loadTable();
      return true;
    } catch (e: any) {
      message(e.message || (mode === "create" ? "创建失败" : "保存失败"), {
        type: "error"
      });
      return false;
    }
  }

  /** 删除业务域（批量软删）。全局域不可删（后端校验 global，前端无 global 字段无法预判，
   *  🔧 Resp 缺 global 登记 T-PERM-026；mock 已校验全局域不可删）。 */
  async function handleDeleteBizDomain(ids: number[]): Promise<boolean> {
    try {
      await removeBizDomain(ids);
      message("删除成功", { type: "success" });
      // 若删除了当前选中域，清空子表
      if (currentDomain.value && ids.includes(currentDomain.value.id)) {
        currentDomain.value = null;
        configData.value = [];
      }
      await loadTable();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  /** 提交域配置表单（save upsert，extra 提交前 JSON.parse 校验）。
   *  需当前选中域的 domainCode。返回 true 表示提交成功。 */
  async function handleSubmitConfig(
    form: DomainConfigFormData
  ): Promise<boolean> {
    if (!currentDomain.value) {
      message("请先选择业务域", { type: "warning" });
      return false;
    }
    const parsed = parseExtra(form.extra);
    if (!parsed.ok) {
      message(parsed.error || "配置值必须是合法 JSON", { type: "error" });
      return false;
    }
    try {
      await saveDomainConfig({
        domainCode: currentDomain.value.code,
        configType: form.configType,
        extra: form.extra.trim()
      });
      message("保存成功", { type: "success" });
      await loadConfigs();
      return true;
    } catch (e: any) {
      message(e.message || "保存失败", { type: "error" });
      return false;
    }
  }

  /** 删除域配置（批量软删） */
  async function handleDeleteConfig(ids: number[]): Promise<boolean> {
    try {
      await removeDomainConfig(ids);
      message("删除成功", { type: "success" });
      await loadConfigs();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  onMounted(() => {
    loadTable();
  });

  return {
    // 主表
    tableData,
    loading,
    searchForm,
    pagination,
    currentDomain,
    loadTable,
    onSearch,
    onReset,
    onPageChange,
    onPageSizeChange,
    selectDomain,
    handleSubmitBizDomain,
    handleDeleteBizDomain,
    // 子表
    configData,
    configLoading,
    loadConfigs,
    handleSubmitConfig,
    handleDeleteConfig,
    // 工厂
    createEmptyBizDomainForm,
    createEmptyDomainConfigForm
  };
}
