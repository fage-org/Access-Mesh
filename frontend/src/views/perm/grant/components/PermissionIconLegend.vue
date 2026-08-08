<script setup lang="ts">
/** 权限图标图例：只解释正交视觉维度，不穷举组合。 */
import ChildPermissionIndicator from "./ChildPermissionIndicator.vue";
import PermissionGlyph from "./PermissionGlyph.vue";
</script>

<template>
  <div class="permission-icon-legend" aria-label="权限图标说明">
    <span class="legend-title">形态</span>
    <span class="legend-item" title="直接授予当前资源的权限">
      <PermissionGlyph variant="valid" />
      <span>直接</span>
    </span>
    <span class="legend-item" title="从祖先资源继承">
      <PermissionGlyph variant="valid" :solid="false" arrow="up" />
      <span>资源继承</span>
    </span>
    <span class="legend-item" title="由更高操作覆盖">
      <PermissionGlyph variant="valid" :solid="false" arrow="right" />
      <span>操作继承</span>
    </span>
    <span class="legend-item" title="同时经过资源继承和操作继承">
      <PermissionGlyph variant="valid" :solid="false" arrow="combined" />
      <span>组合继承</span>
    </span>
    <span class="legend-item" title="全部生效来源均受条件约束">
      <PermissionGlyph variant="valid" striped />
      <span>有条件</span>
    </span>
    <span class="legend-item" title="该权限允许继续转授">
      <PermissionGlyph variant="valid" bold />
      <span>可转授</span>
    </span>
    <span class="legend-item" title="独立展示该主权限包含的子权限数量">
      <ChildPermissionIndicator :count="2" />
      <span>子权限</span>
    </span>
    <span class="legend-title state-title">颜色</span>
    <span class="legend-item" title="绿色表示当前有效或本次新增">
      <PermissionGlyph variant="valid" />
      <span>有效/新增</span>
    </span>
    <span class="legend-item" title="黄色表示待更新；形态含义与绿色一致">
      <PermissionGlyph variant="update" />
      <span>待更新</span>
    </span>
    <span class="legend-item" title="红色表示待撤销；形态含义与绿色一致">
      <PermissionGlyph variant="remove" />
      <span>待撤销</span>
    </span>
  </div>
</template>

<style lang="scss" scoped>
.permission-icon-legend {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
  min-width: 0;
  padding: 4px var(--space-3);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-extra-light);
  border-radius: var(--radius-md);

  .legend-title {
    flex-shrink: 0;
    padding-right: var(--space-2);
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    border-right: 1px solid var(--el-border-color-lighter);

    &.state-title {
      margin-left: var(--space-1);
    }
  }

  .legend-item {
    display: inline-flex;
    flex-shrink: 0;
    gap: 5px;
    align-items: center;
    min-height: 28px;
    font-size: 11px;
    white-space: nowrap;
  }
}
</style>
