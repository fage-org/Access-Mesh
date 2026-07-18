/**
 * V2 矩阵数据 composable（T-FE-031）。
 *
 * 范围：资源类型列表 / 资源树 / 操作列表 / 条件候选 的加载与竞态保护。
 * 只读端点复用共享 @/api/permission-grant（不进 transport，单一事实源）。
 *
 * 设计依据：docs/design/frontend/permission-grant-state-model.md §5.1
 * - switchResourceType 不影响草稿（grantTasks 跨资源类型保留）
 * - 条件候选不随资源类型重复加载（只加载一次）
 * - 树和操作请求有序号保护，避免快速切换串数据
 *
 * 竞态保护（P1 修复）：
 * - loadTypesSeq / loadMatrixSeq 单调递增，resetMatrix 不重置 ->
 *   旧角色在途请求 seq < 新请求 seq，响应校验失败被丢弃，不覆盖新数据
 * - switchResourceType 开头清空 resourceTree/operationList ->
 *   避免切换期间"新类型 + 旧资源/操作"的错误坐标任务
 */
import { ref, computed, type Ref } from "vue";
import {
  getResourceTypeList,
  getResourceTree,
  getOperationList,
  getConditionList,
  type ResourceTypeItem,
  type ResourceTreeNode,
  type OperationItem,
  type ConditionOption
} from "@/api/permission-grant";
import { message } from "@/utils/message";

export function useV2MatrixData(domainCode: Ref<string>) {
  const resourceTypes = ref<ResourceTypeItem[]>([]);
  const currentResourceTypeCode = ref<string>("");
  const resourceTree = ref<ResourceTreeNode[]>([]);
  const operationList = ref<OperationItem[]>([]);
  const conditionOptions = ref<ConditionOption[]>([]);
  const loadingMatrix = ref(false);

  const currentResourceType = computed<ResourceTypeItem | null>(
    () =>
      resourceTypes.value.find(
        t => t.resourceTypeCode === currentResourceTypeCode.value
      ) ?? null
  );

  /** 条件候选只加载一次（不随资源类型/角色重复加载） */
  let conditionLoaded = false;
  /**
   * 请求序号：单调递增，resetMatrix 不重置。
   * 旧角色在途请求 seq < 新请求 seq，响应校验失败被丢弃。
   */
  let loadTypesSeq = 0;
  let loadMatrixSeq = 0;

  /** 加载资源类型列表 + 默认选第一个 + 拉取该类型资源树/操作 */
  async function loadResourceTypes() {
    const dc = domainCode.value;
    const seq = ++loadTypesSeq;
    try {
      const resp = await getResourceTypeList({ domainCode: dc });
      if (seq !== loadTypesSeq) return; // 旧角色响应丢弃
      resourceTypes.value = resp.items;
      if (resp.items.length > 0) {
        await switchResourceType(resp.items[0].resourceTypeCode);
      } else {
        resourceTree.value = [];
        operationList.value = [];
      }
    } catch (e) {
      if (seq !== loadTypesSeq) return;
      message(e instanceof Error ? e.message : "加载资源类型失败", {
        type: "error"
      });
      resourceTypes.value = [];
    }
  }

  /** 加载条件候选（幂等，只加载一次） */
  async function loadConditionOptions() {
    if (conditionLoaded) return;
    try {
      const resp = await getConditionList({ enabledOnly: true });
      conditionOptions.value = resp.items;
      conditionLoaded = true;
    } catch {
      // 条件候选加载失败不阻塞矩阵主流程
    }
  }

  /** 切换资源类型：清空旧数据 + 重载资源树/操作列表（保留草稿） */
  async function switchResourceType(code: string) {
    const dc = domainCode.value;
    const seq = ++loadMatrixSeq;
    loadingMatrix.value = true;
    currentResourceTypeCode.value = code;
    // 清空旧数据：避免切换期间"新类型 + 旧资源/操作"的错误坐标任务
    resourceTree.value = [];
    operationList.value = [];
    try {
      const [treeResp, opResp] = await Promise.all([
        getResourceTree({ domainCode: dc, resourceTypeCode: code }),
        getOperationList({ resourceTypeCode: code })
      ]);
      if (seq !== loadMatrixSeq) return; // 旧请求丢弃
      resourceTree.value = treeResp.items;
      operationList.value = opResp.items;
    } catch (e) {
      if (seq !== loadMatrixSeq) return;
      message(e instanceof Error ? e.message : "加载资源树失败", {
        type: "error"
      });
      resourceTree.value = [];
      operationList.value = [];
    } finally {
      if (seq === loadMatrixSeq) loadingMatrix.value = false;
    }
  }

  /** 重置矩阵数据（切角色时调用；递增序号使旧在途请求失效，避免回写新上下文） */
  function resetMatrix() {
    resourceTypes.value = [];
    currentResourceTypeCode.value = "";
    resourceTree.value = [];
    operationList.value = [];
    loadingMatrix.value = false;
    // 递增序号：旧角色在途请求 seq < 新值，响应校验失败被丢弃
    loadTypesSeq++;
    loadMatrixSeq++;
  }

  return {
    resourceTypes,
    currentResourceType,
    currentResourceTypeCode,
    resourceTree,
    operationList,
    conditionOptions,
    loadingMatrix,
    loadResourceTypes,
    loadConditionOptions,
    switchResourceType,
    resetMatrix
  };
}
