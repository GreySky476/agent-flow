package com.example.agentflow.service;

import java.util.List;

import com.example.agentflow.model.WorkflowState;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RagToolProvider {

    private final WorkflowState state;
    private final List<Integer> knowledgeBaseIds;
    private final int topK;

    public RagToolProvider(WorkflowState state, List<Integer> knowledgeBaseIds, int topK) {
        this.state = state;
        this.knowledgeBaseIds = knowledgeBaseIds;
        this.topK = topK;
    }

    @Tool("搜索知识库获取相关文档内容，当需要查询专业知识或特定文档时使用此工具")
    public String searchKnowledgeBase(String query) {
        log.info("RAG tool invoked with query: {}, kbIds: {}", query, knowledgeBaseIds);
        try {
            HybridSearchService searchService = SpringContextHolder.getBean(HybridSearchService.class);
            if (searchService == null) {
                return "知识库搜索服务暂不可用";
            }
            var results = searchService.search(query, topK);
            if (results == null || results.isEmpty()) {
                return "未找到相关文档内容";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                var doc = results.get(i);
                sb.append("【文档片段 ").append(i + 1).append("】");
                sb.append(doc.text());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("RAG search failed", e);
            return "知识库搜索失败: " + e.getMessage();
        }
    }
}
