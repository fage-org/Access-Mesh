package cn.ac.fage.accessmesh.example.data.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoRecord {
    private Long id;
    private Long tenantId;
    private Long batchId;
    private String recordCode;
    private Long ownerUserId;
    private String teamCode;
    private String departmentCode;
    private String regionCode;
    private java.time.LocalDate saleDate;
    private String productName;
    private java.math.BigDecimal amount;
    private String remark;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
