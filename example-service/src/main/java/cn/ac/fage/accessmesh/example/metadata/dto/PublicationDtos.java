package cn.ac.fage.accessmesh.example.metadata.dto;

import jakarta.validation.constraints.*;

public final class PublicationDtos {
    private PublicationDtos() {}
    public record PublicationGetReq(@NotBlank @Size(max=64) String resourceTypeCode,
        @NotBlank @Size(max=64) String resourceCode, @Size(max=32) String codeType) {}
    public record PublicationPageReq(@Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize,
        @Pattern(regexp="PENDING|PUBLISHED|FAILED") String status) {}
    public record PublicationResp(String resourceTypeCode, String resourceCode, String codeType,
        long sourceVersion, String status, int attempt, String reason) {}
}
