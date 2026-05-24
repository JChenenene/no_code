package com.lingchuang.ai.langgraph4j.v2.model;

import com.lingchuang.ai.langgraph4j.model.ImageCollectionPlan;
import com.lingchuang.ai.langgraph4j.model.ImageResource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 素材规划与收集结果。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AssetPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean degraded;

    private ImageCollectionPlan imageCollectionPlan;

    @Builder.Default
    private List<ImageResource> assets = List.of();

    private String summary;

    private String errorMessage;
}
