<template>
  <div>
    <div class="toolbar">
      <h2>{{ titleMap[code] }}</h2>
      <el-button type="primary" @click="openDialog()">新增</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="author" label="作者" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ row.status === 1 ? '已发布' : '草稿' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑' : '新增'" width="720px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverUrl" placeholder="图片URL" style="margin-bottom:8px" />
          <el-upload :show-file-list="false" :http-request="uploadCover" accept="image/*"><el-button>上传封面</el-button></el-upload>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="8" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status"><el-radio :value="1">发布</el-radio><el-radio :value="0">草稿</el-radio></el-radio-group>
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
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const route = useRoute()
const code = ref(route.params.code)
const list = ref([])
const visible = ref(false)
const titleMap = { NEWS: '新闻管理', NOTICE: '公告管理', POLICY: '政策管理', KNOWLEDGE: '知识库管理' }
const form = reactive({ id: null, categoryCode: code.value, title: '', summary: '', content: '', coverUrl: '', author: '', status: 1 })

const load = async () => {
  const res = await adminApi.contents({ categoryCode: code.value, page: 1, size: 100 })
  list.value = res.data.records
}

const openDialog = (row) => {
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, categoryCode: code.value, title: '', summary: '', content: '', coverUrl: '', author: '', status: 1 })
  visible.value = true
}

const save = async () => {
  const payload = { ...form, categoryCode: code.value }
  if (form.id) await adminApi.updateContent(form.id, payload)
  else await adminApi.createContent(payload)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

const remove = async (id) => {
  await ElMessageBox.confirm('确认删除？')
  await adminApi.deleteContent(id)
  ElMessage.success('已删除')
  load()
}

const uploadCover = async ({ file }) => {
  const res = await adminApi.upload(file)
  form.coverUrl = res.data.url
}

onMounted(load)
watch(() => route.params.code, (v) => { code.value = v; load() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
