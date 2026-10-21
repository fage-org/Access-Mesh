import { computed, onScopeDispose, ref, watch } from "vue";
import { ENUM_PAGE_SIZE } from "@/constants/access";
import { useListLoad, usePagedList } from "@/utils/list-load";
import {
  getSubPermAllowedTypes,
  type SubPermissionPolicy
} from "@/api/permission-grant";
import {
  getResourceAssociationCandidates,
  type ResourceResp
} from "@/api/resource-operation";
import type { EffectiveRecord } from "./grant-plan";

export type ChildParent = Pick<
  EffectiveRecord,
  | "id"
  | "changeId"
  | "resourceTypeCode"
  | "resourceCode"
  | "codeType"
  | "scopeMode"
>;

/** 单父上下文加载，沿用列表层的请求代际与分页，不保存授权草稿。 */
export function useChildCandidates(
  parent: () => ChildParent,
  subject: () => { roleTypeCode: string; roleExternalId: string }
) {
  const selectedType = ref<string | null>(null);
  const contextKey = computed(() => {
    const current = parent();
    return JSON.stringify([
      subject().roleTypeCode,
      subject().roleExternalId,
      current.id,
      current.changeId,
      current.resourceTypeCode,
      current.resourceCode,
      current.codeType,
      current.scopeMode
    ]);
  });
  const policy = useListLoad<SubPermissionPolicy>({
    contextKey: () => contextKey.value,
    errorText: "子权限类型加载失败，请重试",
    fetcher: async () => [
      await getSubPermAllowedTypes({
        domainCode: null,
        ...subject(),
        parentResourceTypeCode: parent().resourceTypeCode,
        ...(parent().scopeMode === "INSTANCE"
          ? {
              parentResourceCode: parent().resourceCode ?? undefined,
              parentCodeType: parent().codeType
            }
          : {})
      })
    ]
  });
  const resources = usePagedList<ResourceResp>({
    initialSize: ENUM_PAGE_SIZE,
    contextKey: () => JSON.stringify([contextKey.value, selectedType.value]),
    errorText: "子权限资源加载失败，请重试",
    fetcher: async (page, size) => {
      const current = parent();
      if (
        !selectedType.value ||
        current.scopeMode === "ALL" ||
        !current.resourceCode
      )
        return { items: [], total: 0 };
      return getResourceAssociationCandidates({
        parentResource: {
          resourceTypeCode: current.resourceTypeCode,
          code: current.resourceCode,
          codeType: current.codeType
        },
        resourceTypeCode: selectedType.value,
        pageNum: page,
        pageSize: size
      });
    }
  });
  watch(
    contextKey,
    () => {
      selectedType.value = null;
      policy.clear();
      resources.clear();
      void policy.load();
    },
    { immediate: true, flush: "sync" }
  );
  watch(
    selectedType,
    () => {
      resources.clear();
      if (selectedType.value && parent().scopeMode !== "ALL")
        void resources.loadTable();
    },
    { flush: "sync" }
  );
  onScopeDispose(() => {
    policy.clear();
    resources.clear();
  });
  return { contextKey, selectedType, policy, resources };
}
