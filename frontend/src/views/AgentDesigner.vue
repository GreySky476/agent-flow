<template>
  <div style="display: flex; height: calc(100vh - 120px)">
    <!-- 左侧节点面板 -->
    <div style="
      width: 180px; border-right: 1px solid #dcdfe6; padding: 12px;
      background: #fafafa; display: flex; flex-direction: column; gap: 8px
    ">
      <div style="font-weight: bold; margin-bottom: 8px">节点类型</div>
      <div
        v-for="nodeType in nodeTypes" :key="nodeType.type"
        draggable="true"
        @dragstart="onDragStart($event, nodeType)"
        style="
          padding: 10px 12px; border-radius: 6px; cursor: grab;
          font-size: 13px; text-align: center; color: #fff; font-weight: 500;
          user-select: none
        "
        :style="{ background: nodeType.color }"
      >
        {{ nodeType.label }}
      </div>

      <el-divider style="margin: 8px 0" />

      <el-button type="primary" size="small" @click="saveWorkflow" :loading="saving">保存</el-button>
      <el-button type="success" size="small" @click="executeWorkflow" :loading="executing" :disabled="!definitionId">执行</el-button>
      <el-input v-model="workflowName" placeholder="工作流名称" size="small" style="margin-top: 4px" />
      <el-input v-model="execParams" placeholder="执行参数 {key:val}" type="textarea" :rows="2" size="small" />

      <div v-if="execResult" style="margin-top: 8px; font-size: 12px">
        <el-tag size="small" :type="execResult.success ? 'success' : 'danger'">
          {{ execResult.success ? '完成' : '失败' }}
        </el-tag>
        <div style="margin-top: 4px">ID: {{ execResult.instanceId }}</div>
      </div>
    </div>

    <!-- 画布 -->
    <div style="flex: 1; height: 100%">
      <VueFlow
        v-model="elements"
        :default-viewport="{ zoom: 1 }"
        :min-zoom="0.2" :max-zoom="4"
        @connect="onConnect"
        @drop="onDrop"
        @dragover="onDragOver"
        @node-double-click="onNodeDoubleClick"
        fit-view-on-init
      >
        <Background />
      </VueFlow>
    </div>

    <!-- Agent 配置弹窗 -->
    <el-dialog v-model="agentDialogVisible" title="配置 Agent" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="Agent 名称">
          <el-select v-model="editingConfig.agentName" placeholder="选择 Agent" style="width: 100%">
            <el-option v-for="a in availableAgents" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型偏好">
          <el-input v-model="editingConfig.modelPreference" placeholder="如 deepseek-v4-flash" />
        </el-form-item>
        <el-form-item label="System Prompt">
          <el-input v-model="editingConfig.systemPrompt" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="agentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAgentConfig">确定</el-button>
      </template>
    </el-dialog>

    <!-- Tool 配置弹窗 -->
    <el-dialog v-model="toolDialogVisible" title="配置工具" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="选择工具">
          <el-select v-model="editingConfig.toolName" placeholder="选择已注册的工具" style="width: 100%" filterable>
            <el-option
              v-for="tool in availableTools" :key="tool.name"
              :label="tool.name + ' - ' + tool.description"
              :value="tool.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工具描述">
          <span style="font-size: 12px; color: #909399">
            {{ availableTools.find(t => t.name === editingConfig.toolName)?.description || '—' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="toolDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveToolConfig">确定</el-button>
      </template>
    </el-dialog>

    <!-- Condition 配置弹窗 -->
    <el-dialog v-model="conditionDialogVisible" title="配置条件" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="条件表达式">
          <el-input v-model="editingConfig.expression" placeholder="#_agent_cs_response.contains('退款')" />
          <div style="font-size: 11px; color: #909399; margin-top: 4px">
            SpEL 表达式，可引用 state 变量。例: #_agent_cs_response.contains('退款')
          </div>
        </el-form-item>
        <el-form-item label="分支说明">
          <el-input v-model="editingConfig.branchDesc" placeholder="如: 是→退款流程 / 否→结单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="conditionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConditionConfig">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import axios from 'axios'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const nodeTypes = [
  { type: 'START', label: 'Start', color: '#67c23a' },
  { type: 'END', label: 'End', color: '#f56c6c' },
  { type: 'AGENT', label: 'Agent', color: '#409eff' },
  { type: 'TOOL', label: 'Tool', color: '#e6a23c' },
  { type: 'CONDITION', label: 'Condition', color: '#909399' }
]

const elements = ref([])
const workflowName = ref('')
const definitionId = ref(null)
const saving = ref(false)
const executing = ref(false)
const execParams = ref('')
const execResult = ref(null)

const agentDialogVisible = ref(false)
const toolDialogVisible = ref(false)
const conditionDialogVisible = ref(false)
const editingNodeId = ref('')
const editingNodeType = ref('')
const editingConfig = ref({})
const availableAgents = ref([])
const availableTools = ref([])

const { addNodes, addEdges } = useVueFlow()
let nodeCounter = 0

onMounted(async () => {
  try {
    const [modelRes, toolRes] = await Promise.all([
      axios.get('/api/v1/admin/models'),
      axios.get('/api/v1/agentflow/tools')
    ])
    availableAgents.value = (modelRes.data || []).map(m => m.name)
    availableTools.value = toolRes.data || []
  } catch (e) {
    availableAgents.value = ['customer_service', 'code_reviewer']
  }
})

function onDragStart(event, nodeType) {
  event.dataTransfer.setData('nodeType', nodeType.type)
  event.dataTransfer.setData('nodeLabel', nodeType.label)
  event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(event) {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

function onDrop(event) {
  const type = event.dataTransfer.getData('nodeType')
  const label = event.dataTransfer.getData('nodeLabel')
  if (!type) return
  const nodeTypeObj = nodeTypes.find(n => n.type === type)
  const id = `${type.toLowerCase()}_${++nodeCounter}`
  const newNode = {
    id, type: 'default',
    position: { x: event.offsetX, y: event.offsetY },
    data: { label, nodeType: type },
    style: {
      background: nodeTypeObj?.color || '#666', color: '#fff',
      padding: '12px 20px', borderRadius: '8px',
      fontSize: '14px', fontWeight: '500', minWidth: '80px', textAlign: 'center'
    }
  }
  addNodes([markRaw(newNode)])
}

function onConnect(connection) {
  addEdges([markRaw({
    id: `e_${connection.source}_${connection.target}`,
    source: connection.source, target: connection.target
  })])
}

function onNodeDoubleClick({ node }) {
  const nt = node.data?.nodeType
  if (!nt || nt === 'START' || nt === 'END') return
  editingNodeId.value = node.id
  editingNodeType.value = nt
  let config = {}
  try { config = JSON.parse(node.data.configJson || '{}') } catch (e) {}

  if (nt === 'AGENT') {
    editingConfig.value = {
      agentName: config.agentName || '',
      modelPreference: config.modelPreference || '',
      systemPrompt: config.systemPrompt || ''
    }
    agentDialogVisible.value = true
  } else if (nt === 'TOOL') {
    editingConfig.value = {
      toolName: config.toolName || ''
    }
    toolDialogVisible.value = true
  } else if (nt === 'CONDITION') {
    editingConfig.value = {
      expression: config.expression || '',
      branchDesc: config.branchDesc || ''
    }
    conditionDialogVisible.value = true
  }
}

function updateNodeConfig() {
  const node = elements.value.find(n => n.id === editingNodeId.value)
  if (!node) return
  node.data.configJson = JSON.stringify(editingConfig.value)
}

function saveAgentConfig() {
  updateNodeConfig()
  editingConfig.value.agentName
    ? elements.value.find(n => n.id === editingNodeId.value).data.label = `Agent: ${editingConfig.value.agentName}`
    : null
  agentDialogVisible.value = false
}

function saveToolConfig() {
  updateNodeConfig()
  editingConfig.value.toolName
    ? elements.value.find(n => n.id === editingNodeId.value).data.label = `Tool: ${editingConfig.value.toolName}`
    : null
  toolDialogVisible.value = false
}

function saveConditionConfig() {
  updateNodeConfig()
  editingNodeType.value === 'CONDITION'
    ? elements.value.find(n => n.id === editingNodeId.value).data.label = `Condition: ${editingConfig.value.expression?.substring(0, 20) || '未配置'}`
    : null
  conditionDialogVisible.value = false
}

async function saveWorkflow() {
  if (!workflowName.value.trim()) { ElMessage.warning('请输入工作流名称'); return }
  saving.value = true
  try {
    const nodes = elements.value.filter(el => el.position)
    const edges = elements.value.filter(el => el.source && el.target)
    const nodeOutputs = {}
    edges.forEach(e => {
      if (!nodeOutputs[e.source]) nodeOutputs[e.source] = []
      nodeOutputs[e.source].push(e.target)
    })
    const nodeList = nodes.map(n => ({
      nodeId: n.id,
      nodeType: n.data?.nodeType || 'AGENT',
      configJson: n.data?.configJson || '{}',
      positionX: Math.round(n.position.x),
      positionY: Math.round(n.position.y),
      nextNodes: (nodeOutputs[n.id] || []).join(',')
    }))
    const definitionJson = JSON.stringify({ nodes: nodeList, edges: edges.map(e => ({ from: e.source, to: e.target })) })
    const res = await axios.post('/api/v1/agentflow/definitions', {
      id: definitionId.value, name: workflowName.value, description: '', status: 'DRAFT',
      definitionJson, nodes: nodeList
    })
    definitionId.value = res.data.id
    ElMessage.success('保存成功 (ID: ' + res.data.id + ')')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally { saving.value = false }
}

async function executeWorkflow() {
  executing.value = true
  execResult.value = null
  try {
    let params = {}
    if (execParams.value.trim()) params = JSON.parse(execParams.value)
    const res = await axios.post(`/api/v1/agentflow/execute/${definitionId.value}`, params)
    execResult.value = res.data
    pollStatus(res.data.instanceId)
  } catch (e) {
    ElMessage.error('执行失败: ' + (e.response?.data?.message || e.message))
    execResult.value = { success: false, instanceId: '', error: e.message }
  } finally { executing.value = false }
}

async function pollStatus(instanceId) {
  try {
    const res = await axios.get(`/api/v1/agentflow/status/${instanceId}`)
    if (res.data.status === 'COMPLETED') { ElMessage.success('工作流执行完成'); return }
    setTimeout(() => pollStatus(instanceId), 2000)
  } catch (e) { console.error('Status poll failed', e) }
}
</script>
