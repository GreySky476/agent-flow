package com.example.agentflow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.agentflow.service.ToolRegistry;
import com.example.agentflow.service.ToolRegistry.ToolEntry;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolMarketController {

    private final ToolRegistry toolRegistry;

    @GetMapping
    public List<Map<String, Object>> listTools() {
        return toolRegistry.getToolMap().values().stream()
                .map(this::toDto)
                .toList();
    }

    @PutMapping("/{toolName}/enable")
    public Map<String, Object> enableTool(@PathVariable String toolName) {
        toolRegistry.enableTool(toolName);
        return Map.of("success", true, "toolName", toolName, "action", "enable");
    }

    @PutMapping("/{toolName}/disable")
    public Map<String, Object> disableTool(@PathVariable String toolName) {
        toolRegistry.disableTool(toolName);
        return Map.of("success", true, "toolName", toolName, "action", "disable");
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadTool(
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam(value = "resources", required = false) List<MultipartFile> resources) {
        String fileName = manifest.getOriginalFilename();
        int resourceCount = resources != null ? resources.size() : 0;

        // TODO: 解析 tool-manifest.yml，验证格式，注册为 MCP 或本地工具
        // manifest 格式示例:
        //   name: my-custom-tool
        //   type: MCP
        //   serverName: custom-server
        //   description: 自定义工具

        return new HashMap<>() {{
            put("success", true);
            put("manifest", fileName);
            put("resourcesCount", resourceCount);
            put("message", "Tool manifest received (placeholder — registration logic TBD)");
        }};
    }

    private Map<String, Object> toDto(ToolEntry entry) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", entry.getName());
        dto.put("source", entry.getType().name());
        dto.put("serverName", entry.getType() == ToolRegistry.ToolType.MCP ? entry.getSource() : null);
        dto.put("description", entry.getDescription());
        dto.put("parameters", entry.getParams());
        dto.put("enabled", entry.isEnabled());
        dto.put("exportable", entry.isExportable());
        return dto;
    }
}
