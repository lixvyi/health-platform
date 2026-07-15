<template>
  <div>
    <h2 style="margin:0 0 16px">API 密钥审核</h2>

    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" stripe v-loading="loading">
          <el-table-column prop="appName" label="应用名称" width="140" />
          <el-table-column label="AppKey" width="280">
            <template #default="{ row }"><code style="font-size:12px;user-select:all">{{ row.appKey }}</code></template>
          </el-table-column>
          <el-table-column prop="owner" label="申请人" width="100" />
          <el-table-column prop="email" label="邮箱" width="150" show-overflow-tooltip />
          <el-table-column prop="organization" label="机构" width="140" show-overflow-tooltip />
          <el-table-column prop="tier" label="套餐" width="70" align="center" />
          <el-table-column label="创建时间" width="100">
            <template #default="{ row }">{{ row.createdAt?.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审批的密钥申请" :image-size="60" />
      </el-tab-pane>

      <el-tab-pane label="已管理" name="managed">
        <el-table :data="managedList" stripe v-loading="loading">
          <el-table-column prop="appName" label="应用名称" width="140" />
          <el-table-column label="AppKey" width="260">
            <template #default="{ row }"><code style="font-size:12px;user-select:all">{{ row.appKey }}</code></template>
          </el-table-column>
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="owner" label="申请人" width="90" />
          <el-table-column prop="tier" label="套餐" width="60" align="center" />
          <el-table-column label="日配额" width="80" align="center">
            <template #default="{ row }">{{ row.dailyQuota?.toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="showEditQuota(row)">配额</el-button>
              <el-button link type="warning" @click="rotate(row)">重置</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑配额 -->
    <el-dialog v-model="quotaVisible" title="编辑配额" width="420px">
      <el-form :model="quotaForm" label-width="100px">
        <el-form-item label="日配额"><el-input-number v-model="quotaForm.dailyQuota" :min="100" :max="9999999" :step="5000" /></el-form-item>
        <el-form-item label="QPS限制"><el-input-number v-model="quotaForm.qpsLimit" :min="1" :max="999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doUpdateQuota">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密钥 -->
    <el-dialog v-model="rotateVisible" title="重置 AppSecret" width="400px">
      <p>确定要重置 <strong>{{ rotating?.appName }}</strong> 的密钥？</p>
      <p style="color:#e6a23c;font-size:13px">原 AppSecret 立即失效，调用方需更新。</p>
      <template #footer>
        <el-button @click="rotateVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="doRotate">确认重置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rotateResultVisible" title="密钥已重置" width="500px">
      <el-alert type="warning" :closable="false" show-icon title="新 AppSecret 仅在此显示一次！" style="margin-bottom:16px" />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="AppKey"><code style="user-select:all">{{ rotating?.appKey }}</code></el-descriptions-item>
        <el-descriptions-item label="新 AppSecret"><code style="user-select:all;word-break:break-all">{{ newSecret }}</code></el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="rotateResultVisible = false; load()">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const tab = ref('pending')
const list = ref([])
const loading = ref(false)
const submitting = ref(false)

const pendingList = computed(() => list.value.filter(r => !r.approvedAt))
const managedList = computed(() => list.value.filter(r => r.approvedAt))

// 配额
const quotaVisible = ref(false)
const quotaForm = ref({ dailyQuota: 50000, qpsLimit: 10 })
const editingQuotaId = ref(null)

// 重置
const rotateVisible = ref(false)
const rotateResultVisible = ref(false)
const rotating = ref(null)
const newSecret = ref('')

const load = async () => {
  loading.value = true
  try {
    const res = await adminApi.apiApps()
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败: ' + (e.message || ''))
  } finally { loading.value = false }
}

const approve = async (row) => {
  try {
    await adminApi.approveApiApp(row.id)
    ElMessage.success('已审批通过，密钥已启用')
    load()
  } catch (e) { ElMessage.error('审批失败: ' + (e.message || '')) }
}

const reject = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝申请', { inputPlaceholder: '可选' })
    await adminApi.toggleApiApp(row.id)
    ElMessage.success('已拒绝')
    load()
  } catch (_) { /* 取消输入 */ }
}

const toggleStatus = async (row) => {
  try {
    await adminApi.toggleApiApp(row.id)
    ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
    load()
  } catch (e) { ElMessage.error('操作失败: ' + (e.message || '')) }
}

const rotate = (row) => { rotating.value = row; rotateVisible.value = true }
const doRotate = async () => {
  submitting.value = true
  try {
    const res = await adminApi.rotateApiSecret(rotating.value.id)
    newSecret.value = res.data?.appSecret || ''
    rotateVisible.value = false
    rotateResultVisible.value = true
  } catch (e) { ElMessage.error('重置失败: ' + (e.message || '')) }
  finally { submitting.value = false }
}

const showEditQuota = (row) => {
  editingQuotaId.value = row.id
  quotaForm.value = { dailyQuota: row.dailyQuota, qpsLimit: row.qpsLimit }
  quotaVisible.value = true
}
const doUpdateQuota = async () => {
  submitting.value = true
  try {
    await adminApi.updateApiAppQuota(editingQuotaId.value, quotaForm.value)
    ElMessage.success('配额已更新')
    quotaVisible.value = false; load()
  } catch (e) { ElMessage.error('更新失败: ' + (e.message || '')) }
  finally { submitting.value = false }
}

onMounted(load)
</script>
