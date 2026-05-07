<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getMenuTree,
  getMenuDetail,
  deleteMenu,
  type MenuNode
} from "@/api/admin/menu";
import MenuTree from "./components/MenuTree.vue";
import MenuForm from "./components/MenuForm.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "MenuManagement"
});

// ========== 状态定义 ==========

const treeData = ref<Array<MenuNode>>([]);
const selectedMenuId = ref<number | null>(null);
const selectedMenuDetail = ref<MenuNode | null>(null);
const loading = ref(false);

// ========== 权限计算 ==========

const canCreate = hasPerms(PERM_CODES.SYS_MENU_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_MENU_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_MENU_DELETE);

// ========== 数据加载 ==========

const loadMenuTree = async () => {
  loading.value = true;
  try {
    const res = await getMenuTree();
    if (res.success) {
      treeData.value = res.data;
    }
  } catch {
    ElMessage.error("加载菜单树失败");
  } finally {
    loading.value = false;
  }
};

const loadMenuDetail = async (menuId: number) => {
  try {
    const res = await getMenuDetail({ id: menuId });
    if (res.success) {
      selectedMenuDetail.value = res.data;
    }
  } catch {
    ElMessage.error("加载菜单详情失败");
  }
};

// ========== 递归查找菜单 ==========

const findMenuInTree = (
  nodes: Array<MenuNode>,
  targetId: number
): MenuNode | null => {
  for (const node of nodes) {
    if (node.id === targetId) return node;
    if (node.children) {
      const found = findMenuInTree(node.children, targetId);
      if (found) return found;
    }
  }
  return null;
};

// ========== 菜单树交互 ==========

const handleMenuNodeClick = async (menuId: number) => {
  selectedMenuId.value = menuId;
  await loadMenuDetail(menuId);
};

const handleCreateRoot = () => {
  selectedMenuId.value = null;
  selectedMenuDetail.value = null;
};

const handleCreateChild = (parentId: number) => {
  selectedMenuId.value = parentId;
  selectedMenuDetail.value = null;
};

const handleDeleteMenu = async (menuId: number) => {
  try {
    await ElMessageBox.confirm(
      "确认删除该菜单?删除后子菜单将一并删除",
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await deleteMenu({ id: menuId });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadMenuTree();
      // 如果删除的是当前选中的菜单，清空选中状态
      if (selectedMenuId.value === menuId) {
        selectedMenuId.value = null;
        selectedMenuDetail.value = null;
      }
    }
  } catch (error) {
    // 区分用户取消和API错误
    if (error !== "cancel") {
      ElMessage.error("删除菜单失败");
    }
  }
};

// ========== 表单成功回调 ==========

const handleFormSuccess = async () => {
  // 捕获当前选中的菜单ID
  const currentMenuId = selectedMenuId.value;
  await loadMenuTree();
  // 验证菜单是否仍然存在
  if (currentMenuId) {
    const menuExists = findMenuInTree(treeData.value, currentMenuId);
    if (menuExists) {
      await loadMenuDetail(currentMenuId);
    } else {
      // 菜单不存在，清空选中状态
      selectedMenuId.value = null;
      selectedMenuDetail.value = null;
    }
  }
};

// ========== 初始化 ==========

onMounted(() => {
  loadMenuTree();
});
</script>

<template>
  <div class="menu-management">
    <div class="flex h-full">
      <!-- 左侧菜单树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            :disabled="!canCreate"
            @click="handleCreateRoot"
          >
            新增根菜单
          </el-button>
        </div>
        <MenuTree
          :data="treeData"
          :loading="loading"
          :selected-menu-id="selectedMenuId"
          :can-create="canCreate"
          :can-delete="canDelete"
          @node-click="handleMenuNodeClick"
          @create-child="handleCreateChild"
          @delete="handleDeleteMenu"
        />
      </div>

      <!-- 右侧详情表单 -->
      <div class="flex-1 p-4">
        <MenuForm
          :menu-detail="selectedMenuDetail"
          :parent-id="selectedMenuId"
          @success="handleFormSuccess"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.menu-management {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
