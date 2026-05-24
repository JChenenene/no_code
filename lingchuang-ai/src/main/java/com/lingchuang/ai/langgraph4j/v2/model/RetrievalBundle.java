package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 检索 Agent 输出。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalBundle implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean enabled;

    private boolean degraded;

    private String retrievalQuery;

    private String summary;

    @Builder.Default
    private List<String> sources = List.of();

    @Builder.Default
    private List<String> snippets = List.of();

    private String errorMessage;
}
