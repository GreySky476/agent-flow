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

      <el-input v-model="workflowName" placeholder="工作流名称" size="small" />
      <el-button type="primary" size="small" @click="saveWorkflow" :loading="saving">保存</el-button>
      <el-button size="small" @click="goBack">返回列表</el-button>
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

    <!-- LLM 配置弹窗 -->
    <el-dialog v-model="llmDialogVisible" title="配置 LLM 节点" width="560px">
      <el-form label-width="110px" size="small">
        <el-form-item label="模型选择">
          <el-select v-model="editingConfig.modelName" placeholder="不选则路由默认模型" style="width: 100%" clearable filterable>
            <el-option v-for="m in availableModels" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="System Prompt">
          <el-input v-model="editingConfig.systemPrompt" type="textarea" :rows="4" placeholder="你是一个智能助手" />
        </el-form-item>
        <el-form-item label="温度 (Temperature)">
          <el-slider v-model="editingConfig.temperature" :min="0" :max="2" :step="0.1" show-input style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">RAG 增强（可选）</el-divider>
        <el-form-item label="启用 RAG">
          <el-switch v-model="editingConfig.ragEnabled" />
        </el-form-item>
        <template v-if="editingConfig.ragEnabled">
          <el-form-item label="知识库 ID">
            <el-input v-model="editingConfig.knowledgeBaseIds" placeholder="多个 ID 用逗号分隔，如 1,2,3" />
          </el-form-item>
          <el-form-item label="返回数量 (topK)">
            <el-input-number v-model="editingConfig.topK" :min="1" :max="20" size="small" />
          </el-form-item>
        </template>
        <el-divider content-position="left">工具增强（可选）</el-divider>
        <el-form-item label="选择工具">
          <el-select
            v-model="editingConfig.toolNames"
            multiple
            filterable
            placeholder="选择已注册的工具"
            style="width: 100%"
          >
            <el-option
              v-for="tool in availableTools" :key="tool.name"
              :label="tool.name + ' - ' + tool.description"
              :value="tool.name"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="llmDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLlmConfig">确定</el-button>
      </template>
    </el-dialog>

    <!-- Branch 配置弹窗 -->
    <el-dialog v-model="branchDialogVisible" title="配置条件分支" width="520px">
      <el-form label-width="110px" size="small">
        <el-form-item label="条件类型">
          <el-select v-model="editingConfig.conditionType" placeholder="选择条件类型" style="width: 100%">
            <el-option label="表达式 (SpEL)" value="EXPRESSION" />
            <el-option label="包含 (Contains)" value="CONTAINS" />
            <el-option label="不包含 (Not Contains)" value="NOT_CONTAINS" />
            <el-option label="正则匹配 (Regex)" value="REGEX" />
            <el-option label="等于 (Equals)" value="EQ" />
            <el-option label="不等于 (Not Equals)" value="NEQ" />
            <el-option label="大于 (Greater Than)" value="GT" />
            <el-option label="小于 (Less Than)" value="LT" />
            <el-option label="大于等于 (GTE)" value="GTE" />
            <el-option label="小于等于 (LTE)" value="LTE" />
          </el-select>
        </el-form-item>
        <template v-if="editingConfig.conditionType === 'EXPRESSION'">
          <el-form-item label="SpEL 表达式">
            <el-input v-model="editingConfig.expression" placeholder="#_llm_node_1_response.contains('退款')" />
            <div style="font-size: 11px; color: #909399; margin-top: 4px">
              例: #_llm_node_1_response.contains('退款')
            </div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="左侧字段">
            <el-input v-model="editingConfig.leftField" placeholder="WorkflowState 中的键名，如 _llm_node_1_response" />
          </el-form-item>
          <el-form-item label="右侧值">
            <el-input v-model="editingConfig.rightValue" placeholder="比较的值" />
          </el-form-item>
        </template>
        <el-form-item label="分支说明">
          <el-input v-model="editingConfig.branchDesc" placeholder="如: 满足条件→True分支" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="branchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBranchConfig">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import axios from 'axios'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const route = useRoute()
const router = useRouter()

const nodeTypes = [
  { type: 'START', label: 'Start', color: '#67c23a' },
  { type: 'END', label: 'End', color: '#f56c6c' },
  { type: 'LLM', label: 'LLM', color: '#409eff' },
  { type: 'BRANCH', label: 'Branch', color: '#909399' }
]

const elements = ref([])
const workflowName = ref('')
const definitionId = ref(null)
const saving = ref(false)

const llmDialogVisible = ref(false)
const branchDialogVisible = ref(false)
const editingNodeId = ref('')
const editingNodeType = ref('')
const editingConfig = ref({})

const availableModels = ref([])
const availableTools = ref([])

const { addNodes, addEdges } = useVueFlow()
let nodeCounter = 0

onMounted(async () => {
  try {
    const [modelRes, toolRes] = await Promise.all([
      axios.get('/api/v1/admin/models'),
      axios.get('/api/v1/agentflow/tools')
    ])
    availableModels.value = (modelRes.data || []).map(m => m.name)
    availableTools.value = toolRes.data || []
  } catch (e) {
    availableModels.value = []
  }

  const id = route.query.id
  if (id && id !== 'new') {
    await loadWorkflow(id)
  }
})

async function loadWorkflow(id) {
  try {
    const res = await axios.get(`/api/v1/agentflow/definitions/${id}`)
    const def = res.data
    workflowName.value = def.name || ''
    definitionId.value = def.id
    const nodes = def.nodes || []
    const defJson = def.definitionJson ? JSON.parse(def.definitionJson) : {}
    const edges = defJson.edges || []

    const loadedNodes = nodes.map((n, i) => ({
      id: n.nodeId || `node_${i}`,
      type: 'default',
      position: { x: n.positionX || 0, y: n.positionY || 0 },
      data: {
        label: getNodeLabel(n.nodeType, n.configJson),
        nodeType: n.nodeType,
        configJson: n.configJson || '{}'
      },
      style: getNodeStyle(n.nodeType)
    }))
    const loadedEdges = edges.map((e, i) => ({
      id: `e_${e.from}_${e.to}_${i}`,
      source: e.from,
      target: e.to
    }))

    elements.value = [...loadedNodes, ...loadedEdges]
    nodeCounter = nodes.length
  } catch (e) {
    ElMessage.error('加载工作流失败: ' + (e.response?.data?.message || e.message))
  }
}

function getNodeLabel(nodeType, configJson) {
  if (nodeType === 'START') return 'Start'
  if (nodeType === 'END') return 'End'
  if (nodeType === 'LLM') {
    try {
      const cfg = JSON.parse(configJson || '{}')
      return `LLM: ${cfg.systemPrompt?.substring(0, 15) || '未配置'}`
    } catch (e) { return 'LLM' }
  }
  if (nodeType === 'BRANCH' || nodeType === 'CONDITION') {
    try {
      const cfg = JSON.parse(configJson || '{}')
      return `Branch: ${cfg.conditionType || '未配置'}`
    } catch (e) { return 'Branch' }
  }
  return nodeType || 'Node'
}

function getNodeStyle(nodeType) {
  const colorMap = {
    START: '#67c23a', END: '#f56c6c', LLM: '#409eff',
    BRANCH: '#909399', CONDITION: '#909399'
  }
  return {
    background: colorMap[nodeType] || '#666', color: '#fff',
    padding: '12px 20px', borderRadius: '8px',
    fontSize: '14px', fontWeight: '500', minWidth: '80px', textAlign: 'center'
  }
}

function goBack() {
  router.push('/workflows')
}

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
    data: { label, nodeType: type, configJson: '{}' },
    style: getNodeStyle(type)
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

  if (nt === 'LLM') {
    editingConfig.value = {
      modelName: config.modelName || '',
      systemPrompt: config.systemPrompt || '你是一个智能助手',
      temperature: config.temperature ?? 0.7,
      ragEnabled: !!config.rag,
      knowledgeBaseIds: config.rag?.knowledgeBaseIds?.join(',') || '',
      topK: config.rag?.topK || 5,
      toolNames: config.toolNames || []
    }
    llmDialogVisible.value = true
  } else if (nt === 'BRANCH' || nt === 'CONDITION') {
    editingConfig.value = {
      conditionType: config.conditionType || 'EXPRESSION',
      expression: config.expression || '',
      leftField: config.leftField || '',
      rightValue: config.rightValue || '',
      branchDesc: config.branchDesc || ''
    }
    branchDialogVisible.value = true
  }
}

function updateNodeConfig() {
  const node = elements.value.find(n => n.id === editingNodeId.value)
  if (!node) return
  // 对 LLM 节点做结构化处理
  if (editingNodeType.value === 'LLM') {
    const cfg = { ...editingConfig.value }
    if (cfg.ragEnabled) {
      const ids = cfg.knowledgeBaseIds
        ? cfg.knowledgeBaseIds.split(',').map(s => parseInt(s.trim())).filter(n => !isNaN(n))
        : []
      cfg.rag = { knowledgeBaseIds: ids, topK: cfg.topK || 5 }
    } else {
      cfg.rag = null
    }
    delete cfg.ragEnabled
    delete cfg.knowledgeBaseIds
    delete cfg.topK
    node.data.configJson = JSON.stringify(cfg)
  } else {
    node.data.configJson = JSON.stringify(editingConfig.value)
  }
}

function saveLlmConfig() {
  updateNodeConfig()
  editingConfig.value.systemPrompt
    ? elements.value.find(n => n.id === editingNodeId.value).data.label = `LLM: ${editingConfig.value.systemPrompt.substring(0, 15)}`
    : null
  llmDialogVisible.value = false
}

function saveBranchConfig() {
  updateNodeConfig()
  elements.value.find(n => n.id === editingNodeId.value).data.label = `Branch: ${editingConfig.value.conditionType || '未配置'}`
  branchDialogVisible.value = false
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
      nodeType: n.data?.nodeType || 'LLM',
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
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally { saving.value = false }
}
</script>
