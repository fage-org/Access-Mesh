<script setup lang="ts">
import { computed } from "vue";
import type { menuType } from "@/layout/types";
import { externalMenuHref } from "./sidebar-link";

const props = defineProps<{
  to: menuType;
}>();

const externalHref = computed(() => externalMenuHref(props.to));
const isExternalLink = computed(() => externalHref.value !== undefined);
const getLinkProps = (item: menuType) => {
  if (isExternalLink.value) {
    return {
      href: externalHref.value,
      target: "_blank",
      rel: "noopener"
    };
  }
  return {
    to: item
  };
};
</script>

<template>
  <component
    :is="isExternalLink ? 'a' : 'router-link'"
    v-bind="getLinkProps(to)"
  >
    <slot />
  </component>
</template>
