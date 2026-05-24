package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 重排序服务。
 */
@Service
@Slf4j
public class RerankService {

    private final ScoringModel scoringModel;

    public RerankService(ScoringModel scoringModel) {
        this.scoringModel = scoringModel;
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (CollUtil.isEmpty(candidates)) {
            return List.of();
        }
        List<RetrievedChunk> rerankedChunks = new ArrayList<>();
        List<Double> scores = scoringModel.scoreAll(
                candidates.stream()
                        .map(chunk -> TextSegment.from(chunk.getContent()))
                        .toList(),
                query
        ).content();
        int limit = Math.min(scores.size(), candidates.size());
        for (int index = 0; index < limit; index++) {
            RetrievedChunk originalChunk = candidates.get(index);
            rerankedChunks.add(originalChunk.toBuilder()
                    .score(scores.get(index))
                    .scoreSource("rerank")
                    .build());
        }
        rerankedChunks.sort(Comparator.comparing(RetrievedChunk::getScore).reversed()
                .thenComparing(RetrievedChunk::getChunkId));
        if (rerankedChunks.size() > topK) {
            List<RetrievedChunk> results = new ArrayList<>(rerankedChunks.subList(0, topK));
            log.info("RAG Rerank 完成，queryHash={}, candidates={}, returned={}",
                    query.hashCode(), candidates.size(), results.size());
            return results;
        }
        log.info("RAG Rerank 完成，queryHash={}, candidates={}, returned={}",
                query.hashCode(), candidates.size(), rerankedChunks.size());
        return rerankedChunks;
    }
}
