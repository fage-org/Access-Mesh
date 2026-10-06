package cn.ac.fage.accessmesh.access.sync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SyncStatusListReq(@Size(max = 128) String sourceService,
                                @Min(1) Integer pageNum,
                                @Min(1) @Max(200) Integer pageSize) {}
