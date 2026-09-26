package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;
import java.util.Objects;

/** 一次实际完成阶段的不可变事实；未请求的 raw 档位由 Details.loadedSections 区分。 */
public record StageFacts(Stage stage, List<GrantFact> rawAfterContext,
                         List<GrantFact> retainedAfterEvaluation, Status stageStatus) {
    public StageFacts {
        Objects.requireNonNull(stage);
        Objects.requireNonNull(stageStatus);
        rawAfterContext = List.copyOf(rawAfterContext);
        retainedAfterEvaluation = List.copyOf(retainedAfterEvaluation);
    }

    public enum Status { PRESENT, FILTERED_EMPTY, NO_MATCH }
}
