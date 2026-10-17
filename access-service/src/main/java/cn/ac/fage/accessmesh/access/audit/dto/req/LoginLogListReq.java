package cn.ac.fage.accessmesh.access.audit.dto.req;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
/** Login time bounds are inclusive UTC values. sort retains its legacy accepted-but-ignored behavior. */
public record LoginLogListReq(@Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize,
    String sort, LocalDateTime since, LocalDateTime until) {
    public int getPageNum() { return pageNum == null ? 1 : pageNum; }
    public int getPageSize() { return pageSize == null ? 20 : pageSize; }
}
