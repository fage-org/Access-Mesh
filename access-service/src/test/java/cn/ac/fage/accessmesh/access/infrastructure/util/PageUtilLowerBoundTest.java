package cn.ac.fage.accessmesh.access.infrastructure.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分页下界行为锁（2026-10-06 拍板：显式 &lt;=0 拒绝、null 才走缺省）。
 * <p>
 * 旧实现 pageNum/pageSize 对显式 &lt;=0 静默归一（1/20），与 @Min(1) 族 DTO 形成两套语义，
 * 调用方传 0 的 bug 被吞进「看似正常」的返回；拍板 PageUtil 分流统一（公共层一处收口 7 端点）。
 * 红跑实证：旧实现下两个「显式非法拒绝」用例失败（静默返回 1/20 不抛）。
 * </p>
 */
class PageUtilLowerBoundTest {

    @Test
    @DisplayName("null 缺省：pageNum→1、pageSize→20")
    void nullFallsBackToDefaults() {
        assertThat(PageUtil.pageNum(null)).isEqualTo(1);
        assertThat(PageUtil.pageSize(null)).isEqualTo(20);
    }

    @Test
    @DisplayName("显式 <=0 拒绝：pageNum 0/-3 抛 IllegalArgumentException（不再静默归 1）")
    void explicitNonPositivePageNumRejected() {
        assertThatThrownBy(() -> PageUtil.pageNum(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageUtil.pageNum(-3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("显式 <=0 拒绝：pageSize 0/-1 抛 IllegalArgumentException（不再静默归 20）")
    void explicitNonPositivePageSizeRejected() {
        assertThatThrownBy(() -> PageUtil.pageSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageUtil.pageSize(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("合法边界：pageNum=1、pageSize=200 直通，pageSize=201 仍拒绝")
    void validBoundsPassThrough() {
        assertThat(PageUtil.pageNum(1)).isEqualTo(1);
        assertThat(PageUtil.pageSize(200)).isEqualTo(200);
        assertThatThrownBy(() -> PageUtil.pageSize(201)).isInstanceOf(IllegalArgumentException.class);
    }
}
