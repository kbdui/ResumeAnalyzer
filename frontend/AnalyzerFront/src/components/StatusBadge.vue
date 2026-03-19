<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string
}>()

const type = computed(() => {
  const s = (props.status || '').toUpperCase()
  if (s === 'DONE' || s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
})

const text = computed(() => (props.status || 'UNKNOWN').toUpperCase())
</script>

<template>
  <el-tag :type="type" effect="dark" round class="status-badge">{{ text }}</el-tag>
</template>

<style scoped>
.status-badge {
  letter-spacing: 0.3px;
  font-weight: 600;
}
</style>
