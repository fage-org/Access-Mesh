package cn.ac.fage.accessmesh.example.data.dto;

import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public final class PolicyDtos {
    private PolicyDtos() {}
    public record GetReq(@NotBlank @Size(max=64) String datasetCode) {}
    public record SaveReq(@NotBlank @Size(max=64) String datasetCode,
        @NotNull @PositiveOrZero Long expectedVersion,@NotNull JsonNode configuration) {}
    public record PolicyResp(String datasetCode,long version,JsonNode configuration,LocalDateTime updatedAt) {}
}
