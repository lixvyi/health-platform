<template>
  <el-dialog v-model="visible" :title="title" width="520px" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="项目名称" required><el-input v-model="form.projectName" /></el-form-item>
      <el-form-item label="使用用途" required><el-input v-model="form.purpose" /></el-form-item>
      <el-form-item label="申请说明"><el-input v-model="form.reason" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({
  title: { type: String, default: '提交使用申请' },
  submitFn: { type: Function, required: true }
})
const emit = defineEmits(['success'])
const loading = ref(false)
const form = reactive({ projectName: '', purpose: '', reason: '' })

const submit = async () => {
  if (!form.projectName || !form.purpose) {
    ElMessage.warning('请填写项目名称和使用用途')
    return
  }
  loading.value = true
  try {
    await props.submitFn({ ...form })
    ElMessage.success('申请已提交，请等待管理员审核')
    visible.value = false
    emit('success')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
