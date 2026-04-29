<template>
  <div>
    <h2 style="margin-bottom: 20px">RAG 文档检索测试</h2>
    <el-row :gutter="20">
      <!-- 左侧：文档上传 -->
      <el-col :span="8">
        <el-card header="文档上传">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :accept="'.pdf,.docx,.ppt,.pptx,.md,.txt,.csv,.html,.png,.jpg,.jpeg,.mp3,.wav'"
          >
            <template #trigger>
              <el-button type="primary">选择文件</el-button>
            </template>
            <template #tip>
              <div style="margin-top: 8px; color: #909399; font-size: 12px">
                支持 PDF / DOCX / PPT / PPTX / MD / TXT / CSV / HTML / 图片 / 音频 (最大 50MB)
              </div>
            </template>
          </el-upload>

          <!-- 文件大小检查 -->
          <div v-if="sizeWarning" style="margin-top: 8px">
            <el-alert type="warning" :closable="false" title="文件过大" :description="sizeWarning" show-icon />
          </div>

          <el-button
            type="success"
            :loading="uploading"
            :disabled="!currentFile || !!sizeWarning"
            @click="handleUpload"
            style="margin-top: 12px; width: 100%"
          >
            上传并索引
          </el-button>

          <!-- 上传进度条 -->
          <el-progress
            v-if="uploading"
            :percentage="uploadProgress"
            :status="uploadProgress === 100 ? 'success' : undefined"
            style="margin-top: 8px"
          />

          <!-- 上传结果 -->
          <div v-if="uploadResult" style="margin-top: 16px">
            <el-alert type="success" :closable="false" show-icon>
              <template #title>文档已索引</template>
              <div>ID: {{ uploadResult.docId }}</div>
              <div>名称: {{ uploadResult.docName }}</div>
              <div>分块数: {{ uploadResult.chunkCount }}</div>
            </el-alert>
          </div>

          <!-- 图片 OCR 预览 -->
          <div v-if="isImage && ocrPreview" style="margin-top: 12px">
            <div style="font-weight: bold; font-size: 13px; margin-bottom: 4px">图片提取文本</div>
            <div style="
              background: #fff7e6; padding: 8px; border-radius: 4px;
              font-size: 12px; max-height: 150px; overflow-y: auto; white-space: pre-wrap
            ">{{ ocrPreview }}</div>
          </div>
        </el-card>

        <!-- 历史文档列表 -->
        <el-card header="已上传文档" style="margin-top: 16px">
          <div v-if="!docList.length" style="color: #909399; font-size: 12px">暂无</div>
          <div v-for="doc in docList" :key="doc.id" style="
            padding: 6px 0; border-bottom: 1px solid #ebeef5; font-size: 12px
          ">
            <div style="font-weight: 500">{{ doc.docName }}</div>
            <div style="color: #909399">ID: {{ doc.id }} · 分块: {{ doc.chunkCount }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：问答 -->
      <el-col :span="16">
        <el-card header="知识库问答">
          <div style="display: flex; gap: 12px; margin-bottom: 16px">
            <el-input v-model="question" placeholder="输入问题..." @keyup.enter="handleQuery" clearable />
            <el-button type="primary" :loading="querying" @click="handleQuery">提问</el-button>
          </div>

          <div v-if="qaResult" style="margin-top: 16px">
            <el-divider />
            <div style="margin-bottom: 12px">
              <span style="font-weight: bold; font-size: 16px">AI 回答</span>
            </div>
            <div style="background: #f5f7fa; padding: 16px; border-radius: 8px; white-space: pre-wrap; line-height: 1.8; margin-bottom: 20px">{{ qaResult.answer }}</div>

            <div style="font-weight: bold; font-size: 14px; margin-bottom: 12px">引用来源 ({{ qaResult.sources.length }})</div>
            <div v-for="(source, idx) in qaResult.sources" :key="idx" style="margin-bottom: 12px">
              <el-card shadow="hover" size="small">
                <template #header>
                  <span style="font-size: 13px; color: #409eff">来源 {{ idx + 1 }} · {{ source.docName }} · chunk {{ source.chunkIndex }}</span>
                </template>
                <div style="font-size: 13px; color: #606266; line-height: 1.7; max-height: 150px; overflow-y: auto">{{ source.text }}</div>
              </el-card>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const MAX_FILE_SIZE = 50 * 1024 * 1024

const currentFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResult = ref(null)
const ocrPreview = ref('')
const docList = ref([])

const question = ref('')
const querying = ref(false)
const qaResult = ref(null)

const isImage = computed(() => {
  if (!currentFile.value) return false
  const n = currentFile.value.name.toLowerCase()
  return ['.png', '.jpg', '.jpeg', '.gif', '.bmp'].some(ext => n.endsWith(ext))
})

const sizeWarning = computed(() => {
  if (!currentFile.value) return ''
  if (currentFile.value.size > MAX_FILE_SIZE) {
    return `文件大小 ${(currentFile.value.size / 1024 / 1024).toFixed(1)}MB 超过上限 50MB`
  }
  return ''
})

function handleFileChange(file) {
  currentFile.value = file.raw
  uploadResult.value = null
  ocrPreview.value = ''
}

function handleFileRemove() {
  currentFile.value = null
  uploadResult.value = null
  ocrPreview.value = ''
}

async function handleUpload() {
  if (!currentFile.value || sizeWarning.value) return
  uploading.value = true
  uploadProgress.value = 0

  try {
    const formData = new FormData()
    formData.append('file', currentFile.value)
    const res = await axios.post('/api/v1/rag/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    })
    uploadProgress.value = 100
    uploadResult.value = res.data
    if (isImage.value) {
      ocrPreview.value = res.data.ocrText || '(图片文本将通过 Tika 提取)'
    }
    ElMessage.success('文档上传并索引成功 · 分块数: ' + (res.data.chunkCount || 0))
    loadDocList()
  } catch (e) {
    ElMessage.error('上传失败: ' + (e.response?.data?.message || e.message))
  } finally {
    uploading.value = false
  }
}

async function handleQuery() {
  if (!question.value.trim()) return
  querying.value = true
  try {
    const res = await axios.post('/api/v1/rag/query', {
      question: question.value,
      conversationId: ''
    })
    qaResult.value = res.data
  } catch (e) {
    ElMessage.error('查询失败: ' + (e.response?.data?.message || e.message))
  } finally {
    querying.value = false
  }
}

async function loadDocList() {
  try {
    const res = await axios.get('/api/v1/rag/documents?page=1&size=20')
    docList.value = res.data || []
  } catch (e) {
    // 接口暂未提供文档列表端点，忽略
  }
}

onMounted(loadDocList)
</script>
