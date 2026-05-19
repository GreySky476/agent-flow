<template>
  <div style="display: flex; height: calc(100vh - 60px)">
    <!-- 左侧已发布工作流列表 -->
    <div style="
      width: 260px; border-right: 1px solid #dcdfe6; padding: 12px;
      background: #fafafa; display: flex; flex-direction: column;
    ">
      <div style="font-weight: bold; margin-bottom: 8px; font-size: 15px">已发布工作流</div>
      <el-input
        v-model="searchText"
        placeholder="搜索工作流"
        size="small"
        style="margin-bottom: 8px"
        clearable
      />
      <div style="flex: 1; overflow-y: auto">
        <div
          v-for="wf in filteredWorkflows" :key="wf.id"
          @click="selectWorkflow(wf)"
          :style="{
            padding: '10px 12px', marginBottom: '6px', borderRadius: '6px',
            cursor: 'pointer', fontSize: '13px',
            background: selectedWorkflow?.id === wf.id ? '#ecf5ff' : '#fff',
            border: selectedWorkflow?.id === wf.id ? '1px solid #409eff' : '1px solid #ebeef5'
          }"
        >
          <div style="font-weight: 600">{{ wf.name }}</div>
          <div style="font-size: 11px; color: #909399; margin-top: 4px">
            v{{ wf.version }} · {{ wf.description || '无描述' }}
          </div>
        </div>
        <el-empty v-if="filteredWorkflows.length === 0" description="暂无已发布的工作流" :image-size="60" />
      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div style="flex: 1; display: flex; flex-direction: column">
      <div style="
        padding: 12px 20px; border-bottom: 1px solid #ebeef5;
        font-size: 16px; font-weight: 600; background: #fff
      ">
        {{ selectedWorkflow ? selectedWorkflow.name : '请选择一个工作流开始对话' }}
      </div>

      <!-- 消息列表 -->
      <div ref="chatContainer" style="flex: 1; overflow-y: auto; padding: 20px; background: #f5f5f5">
        <el-empty v-if="messages.length === 0" description="发送消息开始对话" :image-size="80" />
        <div v-for="(msg, idx) in messages" :key="idx" :style="{ marginBottom: '16px', display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }">
          <div style="
            max-width: 70%; padding: 10px 14px; border-radius: 8px;
            font-size: 14px; line-height: 1.6; word-break: break-word;
            white-space: pre-wrap
          "
            :style="msg.role === 'user'
              ? { background: '#95ec69', color: '#000' }
              : { background: '#fff', border: '1px solid #ebeef5' }
          ">
            {{ msg.content }}
          </div>
        </div>
        <div v-if="sending" style="display: flex; justify-content: flex-start; margin-bottom: 16px">
          <div style="background: #fff; border: 1px solid #ebeef5; padding: 10px 14px; border-radius: 8px; font-size: 14px">
            <el-icon class="is-loading"><Loading /></el-icon> 思考中...
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div style="padding: 12px 20px; border-top: 1px solid #ebeef5; background: #fff">
        <div style="display: flex; gap: 8px">
          <el-input
            v-model="inputText"
            placeholder="输入消息..."
            @keyup.enter="sendMessage"
            :disabled="!selectedWorkflow || sending"
            size="default"
          />
          <el-button type="primary" @click="sendMessage" :disabled="!selectedWorkflow || sending || !inputText.trim()">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import axios from 'axios'

const searchText = ref('')
const inputText = ref('')
const sending = ref(false)
const publishedWorkflows = ref([])
const selectedWorkflow = ref(null)
const messages = ref([])
const chatContainer = ref(null)
const conversationId = ref('')

const filteredWorkflows = computed(() => {
  if (!searchText.value) return publishedWorkflows.value
  return publishedWorkflows.value.filter(wf =>
    wf.name.toLowerCase().includes(searchText.value.toLowerCase())
  )
})

onMounted(() => fetchPublished())

async function fetchPublished() {
  try {
    const res = await axios.get('/api/v1/agentflow/definitions/published')
    publishedWorkflows.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工作流列表失败')
  }
}

function selectWorkflow(wf) {
  selectedWorkflow.value = wf
  messages.value = []
  inputText.value = ''
  conversationId.value = ''
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || !selectedWorkflow.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  sending.value = true

  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }

  try {
    const res = await axios.post(`/api/v1/agentflow/chat/${selectedWorkflow.value.id}`, {
      message: text,
      conversationId: conversationId.value
    })
    const data = res.data
    messages.value.push({ role: 'assistant', content: data.response || '无响应内容' })
    conversationId.value = data.conversationId || conversationId.value
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '请求失败: ' + (e.response?.data?.message || e.message) })
  } finally {
    sending.value = false
    await nextTick()
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  }
}
</script>
