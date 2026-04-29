import { createRouter, createWebHistory } from 'vue-router'
import ModelManagement from '../views/ModelManagement.vue'
import CallLogs from '../views/CallLogs.vue'
import RAGTest from '../views/RAGTest.vue'
import AgentDesigner from '../views/AgentDesigner.vue'
import ToolMarket from '../views/ToolMarket.vue'

const routes = [
  { path: '/', redirect: '/models' },
  { path: '/models', component: ModelManagement },
  { path: '/call-logs', component: CallLogs },
  { path: '/rag', component: RAGTest },
  { path: '/agent-designer', component: AgentDesigner },
  { path: '/tools', component: ToolMarket }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
