import { ref, reactive, onMounted } from "vue";
import { ElMessageBox } from "element-plus";
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
 * T-PERM-026 收口：biz-domain list 服务端 keyword 过滤（code/name/description）+ 分页
 * （ORDER BY code,id），前端只消费（同 system-config 范式）。
 *
 * 主表 CRUD 区别于 system-config：biz-domain 有独立 create/update/remove 接口，
 * 故 handleSubmitBizDomain 区分 create/edit + handleDeleteBizDomain；edit 以业务键 code 定位。
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
      // T-PERM-026 收口：服务端 keyword 过滤（code/name/description，LIKE）+ 分页，前端只消费。
      const res = await getBizDomainList({
        keyword: searchForm.keyword || undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      });
      tableData.value = res.items;
      pagination.total = res.total;
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

  /** 选中业务域 → 加载该域的 DomainConfig 子表。
   *  canViewConfig=false（无 SYSTEM_CONFIG:VIEW 权限）时只选中域、不发 /list 请求，
   *  避免真实后端产生可避免的 403（index.vue 的「配置」按钮已按 canViewConfig 隐藏入口，
   *  此处为双保险守卫）。 */
  function selectDomain(row: BizDomainResp, canViewConfig = true) {
    currentDomain.value = row;
    // 切域立即清空旧域数据并使在途请求过期（防慢响应把 A 域配置回写到 B 域标题下）
    configData.value = [];
    if (canViewConfig) {
      loadConfigs();
    }
  }

  /** 域配置请求序号：仅最新一次 loadConfigs 可回写（权限查询页 reqSeq 同范式） */
  let configReqSeq = 0;

  /** 加载当前选中域的配置列表 */
  async function loadConfigs() {
    if (!currentDomain.value) {
      configData.value = [];
      return;
    }
    const seq = ++configReqSeq;
    configLoading.value = true;
    try {
      const res = await getDomainConfigList(currentDomain.value.code);
      if (seq !== configReqSeq) return;
      configData.value = res.items;
    } catch (e: any) {
      if (seq !== configReqSeq) return;
      message(e.message || "加载域配置失败", { type: "error" });
      configData.value = [];
    } finally {
      if (seq === configReqSeq) configLoading.value = false;
    }
  }

  /** 提交业务域表单（create/edit 区分——biz-domain 有独立 create/update 接口）。
   *  edit 态以业务键 code 定位（T-PERM-026 收口）；name/description 总是携带表单当前值
   *  （description 空串=显式清空，后端仅 null 表示不更新）。
   *  返回 true 表示提交成功（弹窗可关闭）。 */
  async function handleSubmitBizDomain(
    mode: "create" | "edit",
    form: BizDomainFormData,
    domainCode?: string
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
        if (!domainCode) {
          message("缺少业务域编码", { type: "error" });
          return false;
        }
        await updateBizDomain({
          domainCode,
          name: form.name,
          description: form.description
        });
        message("保存成功", { type: "success" });
        // 若编辑的是当前选中域，同步刷新标题
        if (currentDomain.value?.code === domainCode) {
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

  /** 删除业务域（批量软删）。删除保护（T-PERM-026，20051）：全局域不可删（Resp.global=true
   *  的行由 index.vue 禁用删除按钮预判）；域下仍存在有效域配置时后端拒绝（需先删配置）。
   *  删除前 ElMessageBox.confirm 二次确认（对齐 role/type-def 范式，业务域为持久配置类资源）。 */
  async function handleDeleteBizDomain(ids: number[]): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除选中的 ${ids.length} 个业务域？域下仍存在域配置时将被拒绝（请先删除其配置）。`,
        "删除确认",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      return false; // 取消
    }
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

  /** 删除域配置（批量软删）。删除前 ElMessageBox.confirm 二次确认
   *  （对齐 role/type-def 范式，域配置为持久配置类资源）。 */
  async function handleDeleteConfig(ids: number[]): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除选中的 ${ids.length} 条域配置？`,
        "删除确认",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      return false; // 取消
    }
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
