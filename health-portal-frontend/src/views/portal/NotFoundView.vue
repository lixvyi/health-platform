<template>
  <div class="container page">
    <el-result icon="warning" title="页面不存在" sub-title="你访问的页面可能已移动或删除">
      <template #extra><el-button type="primary" @click="$router.push('/')">返回首页</el-button></template>
    </el-result>
  </div>
</template>

<style scoped>
.page { margin: 24px auto; padding: 30px; background: #fff; border-radius: 8px; }
</style>
