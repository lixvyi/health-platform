<template>
  <el-dialog v-model="visible" :title="tab === 'login' ? '登录' : '注册'" width="440px" destroy-on-close @close="store.closeAuthDialog()">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="申请数据资源或 API 权限需要登录。浏览公开内容无需注册。"
      style="margin-bottom:16px"
    />
    <el-tabs v-model="tab">
      <el-tab-pane label="登录" name="login">
        <el-form :model="loginForm" label-width="80px" @submit.prevent="doLogin">
          <el-form-item label="用户名"><el-input v-model="loginForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password /></el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">登录</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="注册" name="register">
        <el-form :model="regForm" label-width="80px" @submit.prevent="doRegister">
          <el-form-item label="用户名"><el-input v-model="regForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="regForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="regForm.email" /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="regForm.realName" /></el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">注册</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePortalAuthStore } from '../stores/portalAuth'

const store = usePortalAuthStore()
const loading = ref(false)

const visible = computed({
  get: () => store.authDialogVisible,
  set: (v) => { if (!v) store.closeAuthDialog() }
})

const tab = ref('login')
watch(() => store.authDialogTab, (v) => { tab.value = v }, { immediate: true })

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', email: '', realName: '' })

const doLogin = async () => {
  loading.value = true
  try {
    await store.login(loginForm)
    ElMessage.success('登录成功')
    await store.afterAuthSuccess()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const doRegister = async () => {
  loading.value = true
  try {
    await store.register(regForm)
    ElMessage.success('注册成功')
    await store.afterAuthSuccess()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
