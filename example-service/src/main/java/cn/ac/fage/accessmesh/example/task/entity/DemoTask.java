package cn.ac.fage.accessmesh.example.task.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class DemoTask {
    private Long id;
    private Long tenantId;
    private String name;
    private String cron;
    private String selection;
    private Boolean enabled;
    private Long version;
    private LocalDateTime nextScheduledTime;
    private Long deleteFlag;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
