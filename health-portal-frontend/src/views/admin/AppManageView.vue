<template>
  <div>
    <div class="toolbar"><h2>应用中心管理</h2><el-button type="primary" @click="open()">新增</el-button></div>
    <el-table :data="list">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="linkUrl" label="链接" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="del(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="应用" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="图标URL"><el-input v-model="form.iconUrl" /></el-form-item>
        <el-form-item label="链接"><el-input v-model="form.linkUrl" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const list = ref([])
const visible = ref(false)
const form = reactive({ id: null, name: '', iconUrl: '', linkUrl: '', description: '', sortOrder: 0, status: 1 })

const load = async () => { list.value = (await adminApi.apps()).data }
const open = (row) => { Object.assign(form, row || { id: null, name: '', iconUrl: '', linkUrl: '', description: '', sortOrder: 0, status: 1 }); visible.value = true }
const save = async () => {
  if (form.id) await adminApi.updateApp(form.id, form)
  else await adminApi.createApp(form)
  ElMessage.success('保存成功'); visible.value = false; load()
}
const del = async (id) => { await ElMessageBox.confirm('确认删除？'); await adminApi.deleteApp(id); load() }
onMounted(load)
</script>

<style scoped>.toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }</style>
