<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  data: unknown
}>()

const pretty = computed(() => JSON.stringify(props.data ?? {}, null, 2))
</script>

<template>
  <div class="json-box">
    <el-input
      type="textarea"
      :rows="18"
      readonly
      :model-value="pretty"
      class="json-viewer"
    />
  </div>
</template>

<style scoped>
.json-box {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #dce3f1;
}

.json-viewer :deep(textarea) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  background: #0f172a;
  color: #d6e2ff;
  border: none;
}
</style>
