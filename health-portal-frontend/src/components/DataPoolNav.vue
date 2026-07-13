<template>
  <nav class="pool-tabs" aria-label="数据资源池视图">
    <router-link to="/data-pool" exact-active-class="active">
      <el-icon><Collection /></el-icon>
      资源池概览
    </router-link>
    <router-link to="/data-pool/governance" exact-active-class="active">
      <el-icon><DataAnalysis /></el-icon>
      数据治理看板
    </router-link>
  </nav>
</template>

<script setup>
import { Collection, DataAnalysis } from '@element-plus/icons-vue'
</script>

<style scoped>
.pool-tabs {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  margin-bottom: 18px;
  border: 1px solid #d7e1e8;
  border-radius: 7px;
  background: #eaf0f4;
}

.pool-tabs a {
  min-height: 34px;
  padding: 0 14px;
  border-radius: 5px;
  color: #53616b;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.pool-tabs a.active {
  color: #135f75;
  background: #fff;
  box-shadow: 0 1px 3px rgba(24, 52, 63, .12);
}

@media (max-width: 520px) {
  .pool-tabs { display: flex; width: 100%; }
  .pool-tabs a { flex: 1; justify-content: center; padding: 0 8px; }
}
</style>
