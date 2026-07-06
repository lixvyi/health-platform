<template>
  <div class="login-page">
    <el-card class="card">
      <h2>门户管理后台登录</h2>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">登录</el-button>
      </el-form>
      <p class="hint">默认账号 admin / Admin@123</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'Admin@123' })

const submit = async () => {
  loading.value = true
  try {
    const res = await adminApi.login(form)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('user', JSON.stringify(res.data))
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a6fb5, #0d3f66); }
.card { width: 400px; }
.hint { font-size: 12px; color: #888; text-align: center; margin-top: 12px; }
</style>
