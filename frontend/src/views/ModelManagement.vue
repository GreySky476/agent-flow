<template>
  <div>
    <h2 style="margin-bottom: 20px">模型管理</h2>
    <el-table :data="models" border stripe v-loading="loading">
      <el-table-column prop="name" label="模型名称" min-width="140" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column label="健康状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.healthy ? 'success' : 'danger'" size="small" effect="dark">
            {{ row.healthy ? '健康' : '不健康' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTier" label="成本层级" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tierType(row.costTier)" size="small">
            {{ row.costTier }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.healthy"
            type="danger"
            size="small"
            @click="handleDisable(row)"
          >
            禁用
          </el-button>
          <el-button
            v-else
            type="success"
            size="small"
            @click="handleEnable(row)"
          >
            启用
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchModels, enableModel, disableModel } from '../api'

const models = ref([])
const loading = ref(false)

function tierType(tier) {
  return { low: 'info', medium: 'warning', high: 'danger' }[tier] || 'info'
}

async function loadModels() {
  loading.value = true
  try {
    const res = await fetchModels()
    models.value = res.data
  } catch (e) {
    ElMessage.error('加载模型列表失败')
  } finally {
    loading.value = false
  }
}

async function handleEnable(row) {
  try {
    await enableModel(row.name)
    row.healthy = true
    ElMessage.success(`${row.name} 已启用`)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleDisable(row) {
  try {
    await disableModel(row.name)
    row.healthy = false
    ElMessage.success(`${row.name} 已禁用`)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(loadModels)
</script>
