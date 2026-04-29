<template>
  <div>
    <h2 style="margin-bottom: 20px">调用日志</h2>
    <el-table :data="logs" border stripe v-loading="loading">
      <el-table-column prop="createdAt" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="modelName" label="模型" width="140" />
      <el-table-column prop="promptTokens" label="输入 Token" width="100" align="right" />
      <el-table-column prop="completionTokens" label="输出 Token" width="100" align="right" />
      <el-table-column prop="latencyMs" label="耗时" width="100" align="right">
        <template #default="{ row }">
          {{ row.latencyMs }}ms
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="dark">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
    </el-table>
    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchCallLogs } from '../api'

const logs = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

function formatTime(dateStr) {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ')
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await fetchCallLogs(currentPage.value, pageSize.value)
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>
