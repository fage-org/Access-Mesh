<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from "vue";
import echarts from "@/plugins/echarts";
import { type ResourceDependencyResp } from "@/api/resource-dependency";
import { type ResourceTreeNode } from "@/api/resource-operation";

defineOptions({ name: "DependencyGraph" });

const props = defineProps<{
  /** 抽屉可见性（v-model） */
  modelValue: boolean;
  /** 依赖列表（扁平，前端建图） */
  deps: ResourceDependencyResp[];
  /** 全量资源列表（节点名称/类型映射） */
  resourceList: ResourceTreeNode[];
}>();

const emit = defineEmits<{ "update:modelValue": [boolean] }>();

const chartRef = ref<HTMLDivElement>();
let chart: ReturnType<typeof echarts.init> | null = null;

const resourceMap = computed(() => {
  const map = new Map<number, ResourceTreeNode>();
  for (const r of props.resourceList) map.set(r.id, r);
  return map;
});

/** 从 CSS 变量取色（深色模式自适应） */
function cssVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return v || fallback;
}

/** 统计节点入度/出度，用于 symbolSize 加权 */
function buildOption() {
  const nodeMap = new Map<
    number,
    { id: string; name: string; category: string; value: number }
  >();
  const degreeMap = new Map<number, number>();

  for (const d of props.deps) {
    degreeMap.set(
      d.resourceEntityId,
      (degreeMap.get(d.resourceEntityId) ?? 0) + 1
    );
    degreeMap.set(
      d.dependsOnResourceEntityId,
      (degreeMap.get(d.dependsOnResourceEntityId) ?? 0) + 1
    );
    if (!nodeMap.has(d.resourceEntityId)) {
      const r = resourceMap.value.get(d.resourceEntityId);
      nodeMap.set(d.resourceEntityId, {
        id: String(d.resourceEntityId),
        name: r
          ? `${r.name}\n(${r.resourceTypeCode})`
          : `#${d.resourceEntityId}`,
        category: r?.resourceTypeCode ?? "未知",
        value: d.resourceEntityId
      });
    }
    if (!nodeMap.has(d.dependsOnResourceEntityId)) {
      const r = resourceMap.value.get(d.dependsOnResourceEntityId);
      nodeMap.set(d.dependsOnResourceEntityId, {
        id: String(d.dependsOnResourceEntityId),
        name: r
          ? `${r.name}\n(${r.resourceTypeCode})`
          : `#${d.dependsOnResourceEntityId}`,
        category: r?.resourceTypeCode ?? "未知",
        value: d.dependsOnResourceEntityId
      });
    }
  }

  const categoryNames = Array.from(
    new Set(Array.from(nodeMap.values()).map(n => n.category))
  );
  const categories = categoryNames.map(name => ({ name }));

  const nodes = Array.from(nodeMap.values()).map(n => ({
    id: n.id,
    name: n.name,
    category: n.category,
    value: n.value,
    symbolSize: 30 + Math.min(degreeMap.get(Number(n.id)) ?? 0, 6) * 5
  }));

  const edges = props.deps.map(d => ({
    source: String(d.resourceEntityId),
    target: String(d.dependsOnResourceEntityId)
  }));

  return {
    tooltip: {
      formatter: (params: any) => {
        if (params.dataType === "edge") {
          const src = resourceMap.value.get(Number(params.data.source));
          const tgt = resourceMap.value.get(Number(params.data.target));
          return `${src?.name ?? params.data.source} <b>-></b> ${tgt?.name ?? params.data.target}`;
        }
        if (params.dataType === "node") {
          const r = resourceMap.value.get(Number(params.data.value));
          return r
            ? `${r.name}（${r.resourceTypeCode}/${r.code}）`
            : params.data.name;
        }
        return params.name;
      }
    },
    legend: [
      {
        data: categoryNames,
        textStyle: { color: cssVar("--el-text-color-regular", "#606266") }
      }
    ],
    series: [
      {
        type: "graph",
        layout: "force",
        data: nodes,
        links: edges,
        categories,
        roam: true,
        draggable: true,
        label: {
          show: true,
          position: "right",
          color: cssVar("--el-text-color-primary", "#303133"),
          fontSize: 12
        },
        force: {
          repulsion: 320,
          edgeLength: 150,
          gravity: 0.08
        },
        edgeSymbol: ["none", "arrow"],
        edgeSymbolSize: 10,
        lineStyle: {
          color: cssVar("--el-color-primary-light-5", "#a0cfff"),
          width: 1.5,
          curveness: 0.12
        },
        emphasis: {
          focus: "adjacency",
          lineStyle: {
            width: 3,
            color: cssVar("--el-color-primary", "#409eff")
          }
        }
      }
    ]
  };
}

function renderChart() {
  if (!chartRef.value) return;
  if (!chart) chart = echarts.init(chartRef.value);
  chart.setOption(buildOption(), true);
}

function handleResize() {
  chart?.resize();
}

function handleClose() {
  emit("update:modelValue", false);
}

watch(
  () => props.modelValue,
  async val => {
    if (val) {
      await nextTick();
      renderChart();
      window.addEventListener("resize", handleResize);
    } else {
      window.removeEventListener("resize", handleResize);
    }
  }
);

watch(
  () => props.deps,
  () => {
    if (props.modelValue) renderChart();
  }
);

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="资源依赖图"
    direction="rtl"
    size="60%"
    @update:model-value="handleClose"
  >
    <div class="graph-container">
      <el-empty
        v-if="deps.length === 0"
        description="暂无依赖数据"
        class="graph-empty"
      />
      <div v-else ref="chartRef" class="graph-canvas" />
      <div class="graph-legend-tip">
        节点大小反映连接数；箭头方向：源资源 -> 被依赖资源；可拖拽、缩放（滚轮）
      </div>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.graph-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.graph-empty {
  flex: 1;
  place-self: center center;
}

.graph-canvas {
  flex: 1;
  min-height: 0;
}

.graph-legend-tip {
  padding: var(--space-2) var(--space-3);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
