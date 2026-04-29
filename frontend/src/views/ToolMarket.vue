<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>工具市场</h2>
      <div style="display: flex; gap: 12px; align-items: center">
        <el-input
          v-model="searchQuery"
          placeholder="搜索工具名称..."
          clearable
          style="width: 260px"
          size="default"
        />
        <el-button type="primary" @click="uploadDialogVisible = true">
          <el-icon><Upload /></el-icon> 上传工具
        </el-button>
      </div>
    </div>

    <!-- 工具卡片列表 -->
    <el-row :gutter="16">
      <el-col
        v-for="tool in filteredTools" :key="tool.name"
        :span="8" style="margin-bottom: 16px"
      >
        <el-card
          shadow="hover"
          :body-style="{ padding: '16px', cursor: 'pointer' }"
          @click="toggleExpand(tool.name)"
        >
          <div style="display: flex; justify-content: space-between; align-items: flex-start">
            <div>
              <div style="font-weight: bold; font-size: 15px; margin-bottom: 4px">
                {{ tool.name }}
              </div>
              <div style="display: flex; gap: 8px; align-items: center">
                <el-tag :type="tool.source === 'LOCAL' ? '' : 'warning'" size="small">
                  {{ tool.source === 'LOCAL' ? '本地' : 'MCP' }}
                </el-tag>
                <span v-if="tool.serverName" style="font-size: 12px; color: #909399">
                  {{ tool.serverName }}
                </span>
              </div>
            </div>
            <el-switch
              v-model="tool.enabled"
              @change="(val) => handleToggle(tool.name, val)"
              @click.stop
              size="small"
            />
          </div>

          <div style="font-size: 13px; color: #606266; margin-top: 8px">
            {{ tool.description || '暂无描述' }}
          </div>

          <!-- 展开详情 -->
          <el-collapse-transition>
            <div v-if="expandedTools.includes(tool.name)" style="margin-top: 12px" @click.stop>
              <el-divider style="margin: 8px 0" />

              <div style="font-size: 13px">
                <div style="font-weight: bold; margin-bottom: 4px">参数定义</div>
                <el-tag
                  v-for="param in tool.parameters" :key="param"
                  size="small" type="info" style="margin-right: 4px"
                >
                  {{ param }}
                </el-tag>
                <span v-if="!tool.parameters || !tool.parameters.length" style="color: #909399">—</span>
              </div>

              <div style="font-size: 13px; margin-top: 8px">
                <div style="font-weight: bold; margin-bottom: 4px">来源服务器</div>
                <span style="color: #909399">
                  {{ tool.serverName || tool.source === 'LOCAL' ? '本地服务' : '-' }}
                </span>
              </div>

              <div style="font-size: 13px; margin-top: 8px">
                <div style="font-weight: bold; margin-bottom: 4px">导出状态</div>
                <el-tag :type="tool.exportable ? 'success' : 'info'" size="small">
                  {{ tool.exportable ? '可导出' : '不可导出' }}
                </el-tag>
              </div>
            </div>
          </el-collapse-transition>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="!filteredTools.length" style="text-align: center; color: #909399; padding: 40px">
      暂无已注册的工具
    </div>

    <!-- 上传工具对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传工具" width="480px">
      <el-form label-width="80px">
        <el-form-item label="Manifest">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="(f) => manifestFile = f.raw"
            accept=".yml,.yaml"
          >
            <el-button size="small">选择 .yml 文件</el-button>
          </el-upload>
          <div v-if="manifestFile" style="font-size: 12px; color: #67c23a; margin-top: 4px">
            {{ manifestFile.name }}
          </div>
        </el-form-item>
        <el-form-item label="资源文件">
          <el-upload
            :auto-upload="false"
            multiple
            :on-change="(f) => resourceFiles.push(f.raw)"
          >
            <el-button size="small">选择文件</el-button>
          </el-upload>
          <div v-for="(f, i) in resourceFiles" :key="i" style="font-size: 12px; color: #67c23a; margin-top: 2px">
            {{ f.name }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import axios from 'axios'

const tools = ref([])
const searchQuery = ref('')
const expandedTools = ref([])

const uploadDialogVisible = ref(false)
const manifestFile = ref(null)
const resourceFiles = ref([])
const uploading = ref(false)

const filteredTools = computed(() => {
  if (!searchQuery.value) return tools.value
  const q = searchQuery.value.toLowerCase()
  return tools.value.filter(t => t.name.toLowerCase().includes(q))
})

function toggleExpand(name) {
  const idx = expandedTools.value.indexOf(name)
  if (idx >= 0) expandedTools.value.splice(idx, 1)
  else expandedTools.value.push(name)
}

async function handleToggle(name, enabled) {
  try {
    const endpoint = enabled ? 'enable' : 'disable'
    await axios.put(`/api/v1/tools/${name}/${endpoint}`)
    ElMessage.success(`工具 [${name}] 已${enabled ? '启用' : '禁用'}`)
  } catch (e) {
    ElMessage.error('操作失败')
    // 回滚 UI
    const tool = tools.value.find(t => t.name === name)
    if (tool) tool.enabled = !enabled
  }
}

async function handleUpload() {
  if (!manifestFile.value) {
    ElMessage.warning('请选择 manifest 文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('manifest', manifestFile.value)
    resourceFiles.value.forEach(f => formData.append('resources', f))
    await axios.post('/api/v1/tools/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('工具清单已接收')
    uploadDialogVisible.value = false
    manifestFile.value = null
    resourceFiles.value = []
    loadTools()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function loadTools() {
  try {
    const res = await axios.get('/api/v1/tools')
    tools.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工具列表失败')
  }
}

onMounted(loadTools)
</script>
