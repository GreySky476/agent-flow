package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.QaResponse;
import com.example.agentflow.model.QaResponse.SourceChunk;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RagQaService {

    private static final String PROMPT_TEMPLATE = """
            你是一个基于知识库的问答助手。请仅根据以下提供的文档片段回答用户问题。
            如果文档中没有相关信息，请回复"未找到相关数据"。回答时请标注来源。

            文档片段：
            %s

            用户问题：%s
            """;

    private final HybridSearchService hybridSearchService;
    private final SmartChatRouter smartChatRouter;

    public QaResponse answer(String userQuery, String conversationId) {
        List<TextSegment> segments = hybridSearchService.search(userQuery, 5);

        String context = buildContext(segments);
        String prompt = PROMPT_TEMPLATE.formatted(context, userQuery);

        log.debug("RAG prompt length: {} chars, sources: {}", prompt.length(), segments.size());

        ChatRequest chatRequest = ChatRequest.builder().message(prompt).build();
        ChatModel model = smartChatRouter.route(chatRequest);
        String answer = model.chat(prompt);

        List<SourceChunk> sources = new ArrayList<>();
        for (TextSegment seg : segments) {
            sources.add(SourceChunk.builder()
                    .text(seg.text())
                    .docName(seg.metadata().getString("doc_name"))
                    .chunkIndex(seg.metadata().getInteger("chunk_index"))
                    .build());
        }

        return QaResponse.builder().answer(answer).sources(sources).build();
    }

    private String buildContext(List<TextSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            String docName = seg.metadata().getString("doc_name");
            int chunkIdx = seg.metadata().getInteger("chunk_index");
            sb.append("[来源 %d: %s (chunk %d)]\n".formatted(i + 1, docName, chunkIdx));
            sb.append(seg.text()).append("\n\n");
        }
        return sb.toString();
    }
}
