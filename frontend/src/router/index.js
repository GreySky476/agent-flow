import { createRouter, createWebHistory } from 'vue-router'
import ModelManagement from '../views/ModelManagement.vue'
import CallLogs from '../views/CallLogs.vue'
import RAGTest from '../views/RAGTest.vue'
import WorkflowList from '../views/WorkflowList.vue'
import AgentDesigner from '../views/AgentDesigner.vue'
import WorkflowChat from '../views/WorkflowChat.vue'

const routes = [
  { path: '/', redirect: '/models' },
  { path: '/models', component: ModelManagement },
  { path: '/call-logs', component: CallLogs },
  { path: '/rag', component: RAGTest },
  { path: '/workflows', component: WorkflowList },
  { path: '/workflows/designer', component: AgentDesigner },
  { path: '/workflow-chat', component: WorkflowChat }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
