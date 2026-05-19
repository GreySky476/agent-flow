package com.example.agentflow.tool;

import org.springframework.stereotype.Component;

import com.example.agentflow.annotation.AgentTool;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSearchTool {

    @AgentTool(
            name = "web_search",
            description = "在互联网上搜索信息，接收 query 参数，返回相关搜索结果",
            exportable = true
    )
    public String search(String query) {
        log.info("WebSearchTool searching: {}", query);

        // TODO: 接入真实的搜索 API（如 SerpAPI、Bing、Google Custom Search 等）
        return """
                {
                  "results": [
                    {
                      "title": "模拟搜索结果 - %s",
                      "url": "https://example.com/result1",
                      "snippet": "这是关于 '%s' 的模拟搜索结果。请替换为真实的搜索引擎 API。"
                    },
                    {
                      "title": "相关文档 - %s",
                      "url": "https://example.com/result2",
                      "snippet": "更多关于 '%s' 的相关信息展示在这里。"
                    }
                  ]
                }
                """.formatted(query, query, query, query);
    }

    @AgentTool(
            name = "fetch_page",
            description = "抓取指定 URL 的网页内容"
    )
    public String fetchPage(String url) {
        log.info("WebSearchTool fetching: {}", url);
        return "{\"url\": \"" + url + "\", \"content\": \"抓取的网页内容（模拟）\"}";
    }
}
