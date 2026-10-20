package cn.ac.fage.accessmesh.example.task.dto;

import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Selection;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.scheduling.support.CronExpression;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}
    public record CreateReq(@NotBlank @Size(max=128) String name,@NotBlank @Size(max=128) String cron,@NotNull @Valid Selection selection) {
        @AssertTrue @JsonIgnore public boolean isCronValid() { return validCron(cron); }
    }
    public record UpdateReq(@NotNull @Positive Long taskId,@NotNull @Positive Long expectedVersion,
        @NotBlank @Size(max=128) String name,@NotBlank @Size(max=128) String cron,@NotNull @Valid Selection selection) {
        @AssertTrue @JsonIgnore public boolean isCronValid() { return validCron(cron); }
    }
    public record KeyReq(@NotNull @Positive Long taskId) {}
    public record EnabledReq(@NotNull @Positive Long taskId,@NotNull @Positive Long expectedVersion,@NotNull Boolean enabled) {}
    public record TriggerReq(@NotNull @Positive Long taskId,@NotNull UUID triggerKey) {}
    public record PageReq(@Min(1) Integer pageNum,@Min(1) @Max(200) Integer pageSize) {}
    public record TaskResp(long taskId,String name,String cron,Selection selection,boolean enabled,long version) {}
    public static boolean validCron(String cron) {
        if(cron==null) return false;
        var parts=cron.trim().split("\\s+");
        return parts.length==6 && "0".equals(parts[0]) && CronExpression.isValidExpression(cron);
    }
}
