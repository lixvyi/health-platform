<template>
  <el-dialog v-model="visible" title="科研人员身份认证" width="520px" destroy-on-close>
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px"
      title="部分数据资源与 API 仅限认证科研人员申请使用。" />
    <el-form :model="form" label-width="100px">
      <el-form-item label="真实姓名" required><el-input v-model="form.realName" /></el-form-item>
      <el-form-item label="所属机构" required><el-input v-model="form.organization" /></el-form-item>
      <el-form-item label="机构类型" required>
        <el-select v-model="form.orgType" style="width:100%">
          <el-option label="高校" value="高校" />
          <el-option label="医院" value="医院" />
          <el-option label="企业" value="企业" />
          <el-option label="研究机构" value="研究机构" />
        </el-select>
      </el-form-item>
      <el-form-item label="研究方向"><el-input v-model="form.researchDirection" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="申请理由" required><el-input v-model="form.certifyReason" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">提交认证</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalUserApi } from '../api/portalUser'
import { usePortalAuthStore } from '../stores/portalAuth'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['success'])
const store = usePortalAuthStore()
const loading = ref(false)
const form = reactive({
  realName: '', organization: '', orgType: '高校', researchDirection: '', certifyReason: ''
})

const submit = async () => {
  if (!form.realName || !form.organization || !form.certifyReason) {
    ElMessage.warning('请填写必填项')
    return
  }
  loading.value = true
  try {
    await portalUserApi.certify(form)
    ElMessage.success('认证申请已提交，请等待管理员审核')
    await store.fetchMe()
    visible.value = false
    emit('success')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
