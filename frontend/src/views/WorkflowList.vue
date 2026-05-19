<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <div style="display: flex; gap: 12px; align-items: center">
        <h2 style="margin: 0">工作流列表</h2>
        <el-input v-model="searchKeyword" placeholder="搜索名称" size="small" style="width: 200px" clearable @clear="fetchList" @keyup.enter="fetchList" />
        <el-select v-model="filterStatus" placeholder="状态" size="small" style="width: 120px" clearable @change="fetchList">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button size="small" @click="fetchList">搜索</el-button>
      </div>
      <div>
        <el-button type="primary" @click="openDesigner()">新建工作流</el-button>
      </div>
    </div>

    <el-table :data="workflows" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="isPublic" label="公开" width="80">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isPublic"
            @change="(val) => togglePublic(row, val)"
            :disabled="row.status !== 'PUBLISHED'"
            size="small"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="openDesigner(row.id)">编辑</el-button>
          <el-button
            v-if="row.status !== 'PUBLISHED'"
            type="success" size="small" link @click="publishWorkflow(row)"
          >发布</el-button>
          <el-button type="warning" size="small" link @click="executeWorkflow(row)">执行</el-button>
          <el-popconfirm title="确定删除此工作流?" @confirm="deleteWorkflow(row.id)">
            <template #reference>
              <el-button type="danger" size="small" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchList"
        @current-change="fetchList"
        small
      />
    </div>

    <!-- 执行弹窗 -->
    <el-dialog v-model="execDialogVisible" title="执行工作流" width="600px">
      <div style="margin-bottom: 12px; font-weight: bold">{{ executingWorkflow?.name }}</div>
      <el-input
        v-model="execInput"
        placeholder="输入内容"
        type="textarea"
        :rows="3"
        style="margin-bottom: 12px"
      />
      <el-button type="primary" @click="runWorkflow" :loading="execRunning">开始执行</el-button>
      <el-divider />
      <div v-if="execOutput !== null || execError" style="max-height: 300px; overflow-y: auto">
        <el-tabs v-model="execTab">
          <el-tab-pane label="输出" name="output">
            <pre style="white-space: pre-wrap; font-size: 13px; background: #f5f5f5; padding: 12px; border-radius: 4px">{{ execOutput || '—' }}</pre>
          </el-tab-pane>
          <el-tab-pane label="错误" name="error">
            <pre style="white-space: pre-wrap; font-size: 13px; color: #f56c6c; background: #fff0f0; padding: 12px; border-radius: 4px">{{ execError || '无' }}</pre>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const workflows = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const execDialogVisible = ref(false)
const executingWorkflow = ref(null)
const execInput = ref('')
const execOutput = ref(null)
const execError = ref('')
const execRunning = ref(false)
const execTab = ref('output')

onMounted(() => fetchList())

async function fetchList() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterStatus.value) params.status = filterStatus.value
    const res = await axios.get('/api/v1/agentflow/definitions', { params })
    workflows.value = res.data.items || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error('加载失败: ' + (e.response?.data?.message || e.message))
  } finally { loading.value = false }
}

function openDesigner(id) {
  if (id) {
    router.push(`/workflows/designer?id=${id}`)
  } else {
    router.push('/workflows/designer?new=1')
  }
}

async function publishWorkflow(row) {
  try {
    await axios.put(`/api/v1/agentflow/definitions/${row.id}/publish`, { isPublic: row.isPublic })
    ElMessage.success('发布成功')
    fetchList()
  } catch (e) {
    ElMessage.error('发布失败: ' + (e.response?.data?.message || e.message))
  }
}

async function togglePublic(row, val) {
  try {
    await axios.put(`/api/v1/agentflow/definitions/${row.id}/public`, { isPublic: val })
    row.isPublic = val
    ElMessage.success(val ? '已设为公开' : '已取消公开')
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

function executeWorkflow(row) {
  executingWorkflow.value = row
  execInput.value = ''
  execOutput.value = null
  execError.value = ''
  execDialogVisible.value = true
}

async function runWorkflow() {
  execRunning.value = true
  execOutput.value = null
  execError.value = ''
  try {
    let params = {}
    if (execInput.value.trim()) {
      params = { userInput: execInput.value }
    }
    const res = await axios.post(`/api/v1/agentflow/execute/${executingWorkflow.value.id}`, params)
    execOutput.value = JSON.stringify(res.data.result, null, 2)
  } catch (e) {
    execError.value = e.response?.data?.message || e.message
  } finally { execRunning.value = false }
}

async function deleteWorkflow(id) {
  try {
    await axios.delete(`/api/v1/agentflow/definitions/${id}`)
    ElMessage.success('已删除')
    fetchList()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.response?.data?.message || e.message))
  }
}

function statusType(status) {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'info' : 'warning'
}

function formatTime(time) {
  if (!time) return '—'
  return new Date(time).toLocaleString('zh-CN')
}
</script>
