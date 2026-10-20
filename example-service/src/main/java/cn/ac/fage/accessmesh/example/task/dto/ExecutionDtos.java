package cn.ac.fage.accessmesh.example.task.dto;

import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Selection;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Column;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ExecutionDtos {
    private ExecutionDtos() {}
    public record SubmitReq(@NotNull UUID triggerKey,@NotNull @Valid Selection selection) {}
    public record KeyReq(@NotNull UUID executionKey) {}
    public record PageReq(@Positive Long taskId,@Min(1) Integer pageNum,@Min(1) @Max(200) Integer pageSize) {}
    public record ExecutionResp(UUID executionKey,Long taskId,String status,int attempt,LocalDateTime scheduledTime,
        Long policyVersion,Long catalogVersion,List<Column> outputColumns,Long recordCount,LocalDateTime expiresAt,String reason) {}
}
