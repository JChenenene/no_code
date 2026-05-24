package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于请求上下文的双路检索 query 构造器。
 */
@Component
public class ContextAwareHybridQueryTransformer implements QueryTransformer {

    private final RetrievalPromptExpansionService retrievalPromptExpansionService;

    public ContextAwareHybridQueryTransformer(RetrievalPromptExpansionService retrievalPromptExpansionService) {
        this.retrievalPromptExpansionService = retrievalPromptExpansionService;
    }

    @Override
    public List<Query> transform(Query query) {
        RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
        if (invocationContext == null || StrUtil.isBlank(query.text())) {
            return List.of(query);
        }
        RetrievalPromptExpansionOutcome expansionOutcome = retrievalPromptExpansionService.expandForUserRequest(
                query.text(),
                invocationContext.getRecentHistories(),
                invocationContext.getCodeGenType()
        );
        String retrievalQuery = expansionOutcome.getRetrievalQuery();
        if (query.metadata() == null) {
            return List.of(Query.from(retrievalQuery));
        }
        return List.of(Query.from(retrievalQuery, query.metadata()));
    }
}
