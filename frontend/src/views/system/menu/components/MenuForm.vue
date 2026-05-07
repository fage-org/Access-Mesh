<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  createMenu,
  updateMenu,
  type MenuNode,
  type MenuCreateRequest,
  type MenuUpdateRequest
} from "@/api/admin/menu";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "MenuForm"
});

const props = defineProps<{
  menuDetail: MenuNode | null;
  parentId: number | null;
}>();

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const formRef = ref<FormInstance>();
const loading = ref(false);
const isEdit = ref(false);

const form = reactive({
  id: undefined as number | undefined,
  parentId: undefined as number | undefined,
  name: "",
  type: 0,
  path: "",
  component: "",
  permCode: "",
  icon: "",
  sort: 0,
  visible: 1,
  status: 1
});

// ========== 权限计算 ==========

const canUpdate = hasPerms(PERM_CODES.SYS_MENU_UPDATE);
const canCreate = hasPerms(PERM_CODES.SYS_MENU_CREATE);

// ========== 表单验证规则 ==========

const rules: FormRules = {
  name: [{ required: true, message: "请输入菜单名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择菜单类型", trigger: "change" }],
  path: [
    {
      required: true,
      message: "请输入路由路径",
      trigger: "blur",
      validator: (_rule, value, callback) => {
        if (form.type === 1 && !value) {
          callback(new Error("菜单类型必须填写路由路径"));
        } else {
          callback();
        }
      }
    }
  ],
  component: [
    {
      required: true,
      message: "请输入组件路径",
      trigger: "blur",
      validator: (_rule, value, callback) => {
        if (form.type === 1 && !value) {
          callback(new Error("菜单类型必须填写组件路径"));
        } else {
          callback();
        }
      }
    }
  ],
  permCode: [
    {
      required: true,
      message: "请输入权限标识",
      trigger: "blur",
      validator: (_rule, value, callback) => {
        if (form.type === 2 && !value) {
          callback(new Error("按钮类型必须填写权限标识"));
        } else {
          callback();
        }
      }
    }
  ]
};

// ========== 监听 menuDetail 变化 ==========

watch(
  () => props.menuDetail,
  detail => {
    if (detail) {
      isEdit.value = true;
      form.id = detail.id;
      form.parentId = detail.parentId;
      form.name = detail.name;
      form.type = detail.type;
      form.path = detail.path || "";
      form.component = detail.component || "";
      form.permCode = detail.permCode || "";
      form.icon = detail.icon || "";
      form.sort = detail.sort;
      form.visible = detail.visible;
      form.status = detail.status;
    } else {
      isEdit.value = false;
      resetForm();
      // 设置父菜单ID（新增子节点时）
      form.parentId = props.parentId;
    }
  },
  { immediate: true }
);

// ========== 重置表单 ==========

const resetForm = () => {
  form.id = undefined;
  form.parentId = undefined;
  form.name = "";
  form.type = 0;
  form.path = "";
  form.component = "";
  form.permCode = "";
  form.icon = "";
  form.sort = 0;
  form.visible = 1;
  form.status = 1;
};

// ========== 提交表单 ==========

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
  } catch {
    return;
  }

  loading.value = true;
  try {
    if (isEdit.value && form.id) {
      const updateData: MenuUpdateRequest = {
        id: form.id,
        name: form.name,
        icon: form.icon,
        sort: form.sort,
        visible: form.visible,
        status: form.status
      };
      // 菜单类型额外字段
      if (form.type === 1) {
        updateData.path = form.path;
        updateData.component = form.component;
      }
      // 按钮类型额外字段
      if (form.type === 2) {
        updateData.permCode = form.permCode;
      }
      const res = await updateMenu(updateData);
      if (res.success) {
        ElMessage.success("更新成功");
        emit("success");
      } else {
        ElMessage.error("更新失败");
      }
    } else {
      const createData: MenuCreateRequest = {
        parentId: form.parentId,
        name: form.name,
        type: form.type,
        icon: form.icon,
        sort: form.sort,
        visible: form.visible,
        status: form.status
      };
      // 菜单类型额外字段
      if (form.type === 1) {
        createData.path = form.path;
        createData.component = form.component;
      }
      // 按钮类型额外字段
      if (form.type === 2) {
        createData.permCode = form.permCode;
        createData.visible = 0; // 按钮类型不显示
      }
      const res = await createMenu(createData);
      if (res.success) {
        ElMessage.success("创建成功");
        emit("success");
      } else {
        ElMessage.error("创建失败");
      }
    }
  } catch {
    ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
  } finally {
    loading.value = false;
  }
};

// ========== 取消 ==========

const handleCancel = () => {
  resetForm();
};
</script>

<template>
  <div class="menu-form">
    <el-alert
      v-if="!props.menuDetail && !props.parentId"
      type="info"
      title="新增根菜单"
      class="mb-4"
      :closable="false"
    />
    <el-alert
      v-if="!props.menuDetail && props.parentId"
      type="info"
      title="新增子菜单"
      class="mb-4"
      :closable="false"
    />
    <el-alert
      v-if="props.menuDetail"
      type="success"
      :title="`编辑菜单: ${props.menuDetail.name}`"
      class="mb-4"
      :closable="false"
    />

    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item v-if="!isEdit" label="上级菜单">
        <el-input
          :value="props.parentId ? '已选择父节点' : '根菜单'"
          disabled
        />
      </el-form-item>

      <el-form-item label="菜单名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入菜单名称" />
      </el-form-item>

      <el-form-item label="菜单类型" prop="type">
        <el-radio-group v-model="form.type" :disabled="isEdit">
          <el-radio :value="0">目录</el-radio>
          <el-radio :value="1">菜单</el-radio>
          <el-radio :value="2">按钮</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 菜单类型特有字段 -->
      <el-form-item v-if="form.type === 1" label="路由路径" prop="path">
        <el-input
          v-model="form.path"
          placeholder="请输入路由路径(如 /system/user)"
        />
      </el-form-item>

      <el-form-item v-if="form.type === 1" label="组件路径" prop="component">
        <el-input
          v-model="form.component"
          placeholder="请输入组件路径(如 system/user/index)"
        />
      </el-form-item>

      <!-- 按钮类型特有字段 -->
      <el-form-item v-if="form.type === 2" label="权限标识" prop="permCode">
        <el-input
          v-model="form.permCode"
          placeholder="请输入权限标识(如 sys:user:add)"
        />
      </el-form-item>

      <!-- 目录和菜单类型字段 -->
      <el-form-item v-if="form.type !== 2" label="图标">
        <el-input
          v-model="form.icon"
          placeholder="请输入图标名称(如 ep/home-filled)"
        />
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="999" />
      </el-form-item>

      <!-- 目录和菜单类型字段 -->
      <el-form-item v-if="form.type !== 2" label="是否显示">
        <el-switch
          v-model="form.visible"
          :active-value="1"
          :inactive-value="0"
        />
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="isEdit ? !canUpdate : !canCreate"
          @click="handleSubmit"
        >
          保存
        </el-button>
        <el-button @click="handleCancel">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.menu-form {
  max-width: 600px;
}
</style>
