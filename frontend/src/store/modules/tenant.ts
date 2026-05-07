import { defineStore } from "pinia";
import { store } from "../utils";

export interface TenantItem {
  id: number;
  name: string;
  code: string;
}

export const useTenantStore = defineStore("tenant", {
  state: () => ({
    currentTenantId: null as number | null,
    tenantList: [] as Array<TenantItem>
  }),
  actions: {
    SET_CURRENT_TENANT(id: number | null) {
      this.currentTenantId = id;
      // 同步到 localStorage
      if (id) {
        localStorage.setItem("currentTenantId", String(id));
      } else {
        localStorage.removeItem("currentTenantId");
      }
    },
    SET_TENANT_LIST(list: Array<TenantItem>) {
      this.tenantList = list;
    },
    // 初始化租户(从 localStorage恢复)
    INIT_TENANT() {
      const savedTenantId = localStorage.getItem("currentTenantId");
      if (savedTenantId) {
        this.currentTenantId = Number(savedTenantId);
      }
    }
  }
});

export function useTenantStoreHook() {
  return useTenantStore(store);
}
