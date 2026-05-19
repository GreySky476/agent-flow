package com.example.agentflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentflow.entity.RagDocument;
import com.example.agentflow.mapper.RagDocumentMapper;
import com.example.agentflow.model.QaResponse;
import com.example.agentflow.service.DocumentProcessingService;
import com.example.agentflow.service.RagQaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rag")
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RagController {

    private final DocumentProcessingService documentProcessingService;
    private final RagQaService ragQaService;
    private final RagDocumentMapper ragDocumentMapper;

    @GetMapping("/documents")
    public List<RagDocument> listDocuments() {
        return ragDocumentMapper.selectList(
                new LambdaQueryWrapper<RagDocument>()
                        .orderByDesc(RagDocument::getId));
    }

    @PostMapping("/documents")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) throws Exception {
        documentProcessingService.processDocument(file);
        RagDocument latest = ragDocumentMapper.selectList(null)
                .stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .findFirst()
                .orElse(null);
        return Map.of(
                "success", true,
                "docId", latest != null ? latest.getId() : 0,
                "docName", file.getOriginalFilename(),
                "chunkCount", latest != null ? latest.getChunkCount() : 0);
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String conversationId = request.getOrDefault("conversationId", "");
        try {
            QaResponse qa = ragQaService.answer(question, conversationId);
            return Map.of("success", true, "answer", qa.getAnswer(), "sources", qa.getSources());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
