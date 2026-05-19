package com.example.agentflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.agentflow.entity.RagDocument;
import com.example.agentflow.mapper.RagDocumentMapper;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Set<String> TEXT_FORMATS = Set.of("md", "txt", "csv", "json", "xml", "html", "htm");
    private static final Set<String> MEDIA_FORMATS = Set.of("png", "jpg", "jpeg", "gif", "bmp",
            "mp3", "wav", "flac", "aac", "ogg", "mp4", "avi", "mov");
    private static final Set<String> METADATA_ONLY_FORMATS = Set.of("zip", "tar", "gz", "7z");

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagDocumentMapper ragDocumentMapper;
    private final ApacheTikaDocumentParser tikaParser = new ApacheTikaDocumentParser();

    @Transactional
    public void processDocument(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String extension = getExtension(fileName);

        if (METADATA_ONLY_FORMATS.contains(extension)) {
            log.warn("File [{}] type [{}] is not supported for text extraction, metadata only", fileName, extension);
            return;
        }

        if (MEDIA_FORMATS.contains(extension)) {
            log.warn("File [{}] type [{}] is media format, limited text extraction via Tika", fileName, extension);
        }

        Document document = parseDocument(file, extension, fileName);
        String text = document.text();
        if (text.isBlank()) {
            log.warn("No text content extracted from [{}]", fileName);
            return;
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.split(document);

        if (segments.isEmpty()) {
            log.warn("No text segments extracted from document [{}]", fileName);
            return;
        }

        RagDocument ragDoc = new RagDocument();
        ragDoc.setDocName(fileName);
        ragDoc.setFileType(extension);
        ragDoc.setChunkCount(segments.size());
        ragDoc.setStatus("INDEXED");
        ragDocumentMapper.insert(ragDoc);

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            segment.metadata()
                    .put("doc_name", fileName)
                    .put("chunk_index", String.valueOf(i))
                    .put("doc_id", String.valueOf(ragDoc.getId()));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        log.info("Indexed document [{}] (id={}) type={} with {} chunks", fileName, ragDoc.getId(), extension, segments.size());
    }

    private Document parseDocument(MultipartFile file, String extension, String fileName) throws IOException {
        if (TEXT_FORMATS.contains(extension)) {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return Document.document(content, Metadata.from("file_name", fileName));
        }
        // .pdf, .docx, .ppt, .pptx, .png, .jpg, .mp3 等全走 Tika
        return tikaParser.parse(file.getInputStream());
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
