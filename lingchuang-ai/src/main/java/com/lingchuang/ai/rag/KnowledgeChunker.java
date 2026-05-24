package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.rag.model.KnowledgeChunk;
import com.lingchuang.ai.rag.model.KnowledgeDocument;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识切分器。
 */
@Component
@NoArgsConstructor
public class KnowledgeChunker {

    private static final int DEFAULT_CHUNK_SIZE = 400;

    private static final int DEFAULT_CHUNK_OVERLAP = 60;

    private int chunkSize = DEFAULT_CHUNK_SIZE;

    private int overlap = DEFAULT_CHUNK_OVERLAP;

    public KnowledgeChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<KnowledgeChunk> split(KnowledgeDocument document) {
        List<String> blocks = splitIntoBlocks(document.getContent());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String block : blocks) {
            if (block.length() > chunkSize && !isCodeFenceBlock(block)) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(buildChunk(document, chunkIndex++, currentChunk.toString().trim()));
                    currentChunk = new StringBuilder();
                }
                for (String part : splitLargeTextBlock(block)) {
                    chunks.add(buildChunk(document, chunkIndex++, part.trim()));
                }
                continue;
            }
            if (!currentChunk.isEmpty() && currentChunk.length() + block.length() + 2 > chunkSize) {
                chunks.add(buildChunk(document, chunkIndex++, currentChunk.toString().trim()));
                currentChunk = new StringBuilder(takeOverlap(currentChunk.toString()));
            }
            if (!currentChunk.isEmpty()) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(block.trim());
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(buildChunk(document, chunkIndex, currentChunk.toString().trim()));
        }
        return chunks;
    }

    private List<String> splitIntoBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean insideCodeFence = false;
        for (String line : content.split("\n")) {
            if (line.strip().startsWith("```")) {
                insideCodeFence = !insideCodeFence;
            }
            buffer.append(line).append("\n");
            if (!insideCodeFence && StrUtil.isBlank(line)) {
                addBlock(blocks, buffer);
            }
        }
        addBlock(blocks, buffer);
        return blocks;
    }

    private void addBlock(List<String> blocks, StringBuilder buffer) {
        String value = buffer.toString().trim();
        if (StrUtil.isNotBlank(value)) {
            blocks.add(value);
        }
        buffer.setLength(0);
    }

    private boolean isCodeFenceBlock(String block) {
        return block.contains("```");
    }

    private List<String> splitLargeTextBlock(String block) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + chunkSize);
            pieces.add(block.substring(start, end));
            if (end == block.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return pieces;
    }

    private String takeOverlap(String content) {
        if (content.length() <= overlap) {
            return content;
        }
        return content.substring(content.length() - overlap);
    }

    private KnowledgeChunk buildChunk(KnowledgeDocument document, int chunkIndex, String content) {
        return KnowledgeChunk.builder()
                .docId(document.getDocId())
                .chunkId(document.getDocId() + "#" + chunkIndex)
                .title(document.getTitle())
                .path(document.getPath())
                .sourceType(document.getSourceType())
                .priority(document.getPriority())
                .checksum(document.getChecksum())
                .tags(document.getTags())
                .codeGenTypes(document.getCodeGenTypes())
                .content(content)
                .build();
    }
}
