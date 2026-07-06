<template>
  <div>
    <el-table :data="rows" stripe border max-height="420" size="small">
      <el-table-column
        v-for="col in columns"
        :key="col"
        :prop="col"
        :label="col"
        min-width="120"
        show-overflow-tooltip
      />
    </el-table>
    <p v-if="total > rows.length" class="more-hint">
      共 {{ total }} 条，当前展示前 {{ rows.length }} 条（完整数据见原始 CSV）
    </p>
  </div>
</template>

<script setup>
defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  total: { type: Number, default: 0 }
})
</script>

<style scoped>
.more-hint { font-size: 12px; color: #888; margin-top: 8px; }
</style>
