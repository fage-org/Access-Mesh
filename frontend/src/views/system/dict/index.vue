<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  listDictTypes,
  pageDictTypes,
  createDictType,
  deleteDictType,
  listDictData,
  createDictData,
  updateDictData,
  deleteDictData,
  type DictTypeItem,
  type DictDataItem
} from "@/api/system/dict";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "DictManagement"
});

// ========== 状态定义 ==========

const typeLoading = ref(false);
const dataLoading = ref(false);
const typeList = ref<Array<DictTypeItem>>([]);
const dataList = ref<Array<DictDataItem>>([]);
const selectedTypeId = ref<number | null>(null);

// 类型分页
const typeTotal = ref(0);
const typePageNum = ref(1);
const typePageSize = ref(20);

// 数据分页
const dataTotal = ref(0);
const dataPageNum = ref(1);
const dataPageSize = ref(20);

// 类型搜索
const typeSearch = reactive({
  code: "",
  name: ""
});

// 类型表单
const typeDialogVisible = ref(false);
const typeForm = reactive({
  id: null as number | null,
  code: "",
  name: "",
  description: "",
  status: 1
});

// 数据表单
const dataDialogVisible = ref(false);
const dataForm = reactive({
  id: null as number | null,
  dictTypeId: null as number | null,
  label: "",
  value: "",
  sort: 0,
  description: "",
  status: 1
});

// 权限
const canCreate = hasPerms(PERM_CODES.SYS_DICT_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_DICT_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_DICT_DELETE);

// ========== 数据加载 ==========

const loadTypeList = async () => {
  typeLoading.value = true;
  try {
    const res = await pageDictTypes({
      pageNum: typePageNum.value,
      pageSize: typePageSize.value
    });
    if (res.success) {
      typeList.value = res.data.list || [];
      typeTotal.value = res.data.total;
    }
  } catch (error) {
    console.error("加载字典类型失败:", error);
  } finally {
    typeLoading.value = false;
  }
};

const loadDataList = async () => {
  if (!selectedTypeId.value) return;
  dataLoading.value = true;
  try {
    const res = await listDictData({ id: selectedTypeId.value });
    if (res.success) {
      dataList.value = res.data || [];
    }
  } catch (error) {
    console.error("加载字典数据失败:", error);
  } finally {
    dataLoading.value = false;
  }
};

const handleTypeSelect = (type: DictTypeItem) => {
  selectedTypeId.value = type.id;
  dataPageNum.value = 1;
  loadDataList();
};

// ========== 类型操作 ==========

const handleCreateType = () => {
  typeForm.id = null;
  typeForm.code = "";
  typeForm.name = "";
  typeForm.description = "";
  typeForm.status = 1;
  typeDialogVisible.value = true;
};

const handleEditType = (type: DictTypeItem) => {
  typeForm.id = type.id;
  typeForm.code = type.code;
  typeForm.name = type.name;
  typeForm.description = type.description || "";
  typeForm.status = type.status;
  typeDialogVisible.value = true;
};

const handleDeleteType = async (type: DictTypeItem) => {
  try {
    await ElMessageBox.confirm(`确认删除字典类型 "${type.name}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteDictType({ ids: [type.id] });
    if (res.success) {
      ElMessage.success("删除成功");
      if (selectedTypeId.value === type.id) {
        selectedTypeId.value = null;
        dataList.value = [];
      }
      await loadTypeList();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除字典类型失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

const handleTypeSubmit = async () => {
  if (!typeForm.code || !typeForm.name) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  try {
    // 类型只有创建，无更新API
    if (typeForm.id) {
      ElMessage.warning("字典类型暂不支持编辑");
      return;
    }
    const res = await createDictType({
      code: typeForm.code,
      name: typeForm.name,
      description: typeForm.description,
      status: typeForm.status
    });
    if (res.success) {
      ElMessage.success("创建成功");
      typeDialogVisible.value = false;
      await loadTypeList();
    }
  } catch (error) {
    console.error("创建字典类型失败:", error);
    ElMessage.error("创建失败");
  }
};

// ========== 数据操作 ==========

const handleCreateData = () => {
  if (!selectedTypeId.value) {
    ElMessage.warning("请先选择字典类型");
    return;
  }
  dataForm.id = null;
  dataForm.dictTypeId = selectedTypeId.value;
  dataForm.label = "";
  dataForm.value = "";
  dataForm.sort = 0;
  dataForm.description = "";
  dataForm.status = 1;
  dataDialogVisible.value = true;
};

const handleEditData = (data: DictDataItem) => {
  dataForm.id = data.id;
  dataForm.dictTypeId = data.dictTypeId;
  dataForm.label = data.label;
  dataForm.value = data.value;
  dataForm.sort = data.sort;
  dataForm.description = data.description || "";
  dataForm.status = data.status;
  dataDialogVisible.value = true;
};

const handleDeleteData = async (data: DictDataItem) => {
  try {
    await ElMessageBox.confirm(`确认删除字典数据 "${data.label}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteDictData({ id: data.id });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadDataList();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除字典数据失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

const handleDataSubmit = async () => {
  if (!dataForm.label || !dataForm.value) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  try {
    if (dataForm.id) {
      const res = await updateDictData({
        id: dataForm.id,
        dictTypeId: dataForm.dictTypeId!,
        label: dataForm.label,
        value: dataForm.value,
        sort: dataForm.sort,
        description: dataForm.description,
        status: dataForm.status
      });
      if (res.success) {
        ElMessage.success("更新成功");
        dataDialogVisible.value = false;
        await loadDataList();
      }
    } else {
      const res = await createDictData({
        dictTypeId: dataForm.dictTypeId!,
        label: dataForm.label,
        value: dataForm.value,
        sort: dataForm.sort,
        description: dataForm.description,
        status: dataForm.status
      });
      if (res.success) {
        ElMessage.success("创建成功");
        dataDialogVisible.value = false;
        await loadDataList();
      }
    }
  } catch (error) {
    console.error("保存字典数据失败:", error);
    ElMessage.error("保存失败");
  }
};

// ========== 分页 ==========

const handleTypePageChange = (page: number) => {
  typePageNum.value = page;
  loadTypeList();
};

const handleTypeSizeChange = (size: number) => {
  typePageSize.value = size;
  typePageNum.value = 1;
  loadTypeList();
};

// ========== 初始化 ==========

onMounted(() => {
  loadTypeList();
});
</script>

<template>
  <div class="dict-management">
    <div class="flex h-full">
      <!-- 左侧: 字典类型列表 -->
      <div class="w-[350px] border-r flex flex-col">
        <div class="p-4 border-b flex items-center justify-between">
          <span class="font-medium">字典类型</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!canCreate"
            @click="handleCreateType"
          >
            新增类型
          </el-button>
        </div>
        <el-table
          v-loading="typeLoading"
          :data="typeList"
          stripe
          highlight-current-row
          @current-change="handleTypeSelect"
        >
          <el-table-column prop="code" label="编码" width="100" />
          <el-table-column prop="name" label="名称" width="120" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'danger'"
                size="small"
              >
                {{ row.status === 1 ? "启用" : "禁用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button
                type="danger"
                link
                size="small"
                :disabled="!canDelete"
                @click.stop="handleDeleteType(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="p-2">
          <el-pagination
            :current-page="typePageNum"
            :page-size="typePageSize"
            :total="typeTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            small
            @current-change="handleTypePageChange"
            @size-change="handleTypeSizeChange"
          />
        </div>
      </div>

      <!-- 右侧: 字典数据列表 -->
      <div class="flex-1 p-4">
        <div v-if="!selectedTypeId" class="text-center py-10 text-gray-500">
          请选择字典类型查看数据
        </div>
        <div v-else>
          <div class="flex items-center justify-between mb-4">
            <span class="font-medium">字典数据</span>
            <el-button
              type="primary"
              size="small"
              :disabled="!canCreate"
              @click="handleCreateData"
            >
              新增数据
            </el-button>
          </div>
          <el-table v-loading="dataLoading" :data="dataList" stripe>
            <el-table-column prop="label" label="标签" width="150" />
            <el-table-column prop="value" label="值" width="150" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 1 ? 'success' : 'danger'"
                  size="small"
                >
                  {{ row.status === 1 ? "启用" : "禁用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="150" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :disabled="!canUpdate"
                  @click="handleEditData(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  :disabled="!canDelete"
                  @click="handleDeleteData(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 字典类型表单弹窗 -->
    <el-dialog v-model="typeDialogVisible" title="新增字典类型" width="400px">
      <el-form :model="typeForm" label-width="80px">
        <el-form-item label="编码" required>
          <el-input v-model="typeForm.code" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="typeForm.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTypeSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据表单弹窗 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="dataForm.id ? '编辑字典数据' : '新增字典数据'"
      width="400px"
    >
      <el-form :model="dataForm" label-width="80px">
        <el-form-item label="标签" required>
          <el-input v-model="dataForm.label" placeholder="请输入标签" />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="dataForm.value" placeholder="请输入值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dataForm.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dataForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDataSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.dict-management {
  height: calc(100vh - 100px);
  padding: 20px;
}
</style>
