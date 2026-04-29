<template>
  <el-container style="min-height: 100vh">
    <el-aside width="200px" style="background-color: #304156">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold">
        Agent Flow
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/models">
          <el-icon><Monitor /></el-icon>
          <span>模型管理</span>
        </el-menu-item>
        <el-menu-item index="/call-logs">
          <el-icon><Document /></el-icon>
          <span>调用日志</span>
        </el-menu-item>
        <el-menu-item index="/rag">
          <el-icon><Search /></el-icon>
          <span>RAG 检索</span>
        </el-menu-item>
        <el-menu-item index="/agent-designer">
          <el-icon><Operation /></el-icon>
          <span>工作流</span>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Switch /></el-icon>
          <span>工具市场</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { Monitor, Document, Search, Operation, Switch } from '@element-plus/icons-vue'
</script>
