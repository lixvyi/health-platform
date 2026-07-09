<template>
  <div>
    <div class="toolbar">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openDialog()">新增用户</el-button>
    </div>

    <!-- 搜索 -->
    <div class="search-row">
      <el-input v-model="keyword" placeholder="搜索用户名/姓名" clearable style="max-width: 280px" @keyup.enter="load" />
      <el-button @click="load">搜索</el-button>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : ''" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 16).replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)" :disabled="row.username === 'admin'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
      style="margin-top: 16px"
    />

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password placeholder="登录密码" />
        </el-form-item>
        <el-form-item label="新密码" v-else>
          <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="编辑" value="EDITOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const visible = ref(false)
const form = reactive({ id: null, username: '', password: '', realName: '', role: 'EDITOR' })

const roleLabel = (role) => ({ ADMIN: '管理员', EDITOR: '编辑' }[role] || role)

const load = async () => {
  const res = await adminApi.users({ keyword: keyword.value, page: page.value, size: 20 })
  list.value = res.data.records
  total.value = res.data.total
}

const openDialog = (row) => {
  if (row) {
    Object.assign(form, { id: row.id, username: row.username, password: '', realName: row.realName, role: row.role })
  } else {
    Object.assign(form, { id: null, username: '', password: '', realName: '', role: 'EDITOR' })
  }
  visible.value = true
}

const save = async () => {
  if (form.id) {
    const payload = { realName: form.realName, role: form.role }
    if (form.password) payload.password = form.password
    await adminApi.updateUser(form.id, payload)
    ElMessage.success('更新成功')
  } else {
    if (!form.username || !form.password) {
      ElMessage.warning('请填写用户名和密码')
      return
    }
    await adminApi.createUser({ username: form.username, password: form.password, realName: form.realName, role: form.role })
    ElMessage.success('创建成功')
  }
  visible.value = false
  load()
}

const toggleStatus = async (row) => {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`)
  await adminApi.toggleUserStatus(row.id)
  ElMessage.success(`已${action}`)
  load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？此操作不可恢复。`)
  await adminApi.deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.search-row { margin-bottom: 16px; display: flex; gap: 10px; }
</style>
