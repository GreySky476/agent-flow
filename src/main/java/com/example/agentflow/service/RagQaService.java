package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.QaResponse;
import com.example.agentflow.model.QaResponse.SourceChunk;
import com.example.agentflow.model.RetrievalResult;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RagQaService {

    private static final String PROMPT_TEMPLATE = """
            你是一个基于知识库和知识图谱的问答助手。请根据以下提供的文档片段和知识图谱信息回答用户问题。
            如果信息不充分，请回复"未找到相关数据"。回答时请标注来源。

            检索结果：
            %s

            用户问题：%s
            """;

    private final GraphRagRetriever graphRagRetriever;
    private final SmartChatRouter smartChatRouter;

    public QaResponse answer(String userQuery, String conversationId) {
        List<RetrievalResult> results = graphRagRetriever.retrieve(userQuery, 5);
        String context = buildContext(results);
        String prompt = PROMPT_TEMPLATE.formatted(context, userQuery);

        log.debug("GraphRAG prompt length: {} chars, sources: {}", prompt.length(), results.size());

        ChatRequest chatRequest = ChatRequest.builder().message(prompt).build();
        ChatModel model = smartChatRouter.route(chatRequest);
        String answer = model.chat(prompt);

        List<SourceChunk> sources = new ArrayList<>();
        for (RetrievalResult r : results) {
            sources.add(SourceChunk.builder()
                    .text(r.getText())
                    .docName(r.getSource() + " (" + r.getType() + ")")
                    .chunkIndex(0)
                    .build());
        }

        return QaResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    private String buildContext(List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("[来源 %d: %s | 类型: %s]\n".formatted(i + 1, r.getSource(), r.getType()));
            sb.append(r.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
