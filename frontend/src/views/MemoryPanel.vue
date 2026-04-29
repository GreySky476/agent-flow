<template>
  <div style="padding: 12px">
    <h3 style="margin-bottom: 12px; font-size: 15px">记忆监控</h3>

    <!-- sessionId 输入 -->
    <el-input v-model="sessionId" placeholder="Session ID" size="small" style="margin-bottom: 12px" @change="refresh" />

    <!-- 状态概览 -->
    <div style="margin-bottom: 12px; font-size: 12px; color: #606266">
      <el-row :gutter="8">
        <el-col :span="12">
          <div style="background: #f5f7fa; padding: 8px; border-radius: 4px; text-align: center">
            <div style="font-size: 18px; font-weight: bold">{{ status.estimatedTokens }}</div>
            <div>Token 估算</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div style="background: #f5f7fa; padding: 8px; border-radius: 4px; text-align: center">
            <div style="font-size: 18px; font-weight: bold">{{ status.shortTermMessages }}</div>
            <div>短期消息</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 压缩按钮 -->
    <el-button
      type="warning" size="small" style="width: 100%; margin-bottom: 12px"
      :loading="compressing" @click="handleCompress"
    >
      手动压缩记忆
    </el-button>

    <!-- 摘要 -->
    <div style="margin-bottom: 12px">
      <div style="font-weight: bold; font-size: 13px; margin-bottom: 4px">对话摘要</div>
      <div style="
        background: #f0f9eb; padding: 8px; border-radius: 4px;
        font-size: 12px; line-height: 1.6; max-height: 120px; overflow-y: auto;
        white-space: pre-wrap; word-break: break-word
      ">
        {{ summary.summary || '暂无摘要（可能需要手动压缩）' }}
      </div>
    </div>

    <!-- 实体列表 -->
    <div>
      <div style="font-weight: bold; font-size: 13px; margin-bottom: 4px">
        实体记忆 ({{ entityCount }})
      </div>
      <div v-if="entityList.length" style="max-height: 200px; overflow-y: auto">
        <div
          v-for="(value, key) in entityList" :key="key"
          style="
            background: #ecf5ff; padding: 6px 10px; border-radius: 4px;
            margin-bottom: 4px; font-size: 12px
          "
        >
          <span style="font-weight: 600; color: #409eff">{{ key }}</span>
          <span style="color: #909399">: </span>
          <span style="color: #606266">{{ value }}</span>
        </div>
      </div>
      <div v-else style="font-size: 12px; color: #909399">暂无实体</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const sessionId = ref('')
const compressing = ref(false)
const summary = reactive({ summary: '' })
const entityList = ref({})
const entityCount = ref(0)
const status = reactive({ shortTermMessages: 0, estimatedTokens: 0 })

async function refresh() {
  if (!sessionId.value) return
  try {
    const [summaryRes, entitiesRes, statusRes] = await Promise.all([
      axios.get(`/api/v1/memory/${sessionId.value}/summary`),
      axios.get(`/api/v1/memory/${sessionId.value}/entities`),
      axios.get(`/api/v1/memory/${sessionId.value}/status`)
    ])
    summary.summary = summaryRes.data.summary || ''
    entityList.value = entitiesRes.data.entities || {}
    entityCount.value = Object.keys(entityList.value).length
    Object.assign(status, statusRes.data)
  } catch (e) {
    console.error('Memory refresh failed', e)
  }
}

async function handleCompress() {
  if (!sessionId.value) return
  compressing.value = true
  try {
    await axios.post(`/api/v1/memory/${sessionId.value}/compress`)
    ElMessage.success('压缩完成')
    await refresh()
  } catch (e) {
    ElMessage.error('压缩失败')
  } finally {
    compressing.value = false
  }
}

watch(sessionId, refresh)
</script>
