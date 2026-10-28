
package gameplatform.recommendation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// 為什麼要使用複合主鍵？
// 我們希望每個會員對每個 Tag 只有一筆統計紀錄。
@Entity
@Table(name = "member_tag_search_stats", schema = "dbo")
@IdClass(MemberTagSearchStatId.class)
@Getter
@Setter
@NoArgsConstructor
public class MemberTagSearchStat {

    // 會員 ID（複合主鍵）
    @Id
    @Column(name = "member_id")
    private Integer memberId;

    // Tag ID（複合主鍵）
    @Id
    @Column(name = "tag_id")
    private Integer tagId;

    // 搜尋次數
    @Column(name = "search_count", nullable = false)
    private Integer searchCount = 1;

    // 第一次搜尋時間
    @Column(name = "first_searched_at", nullable = false)
    private LocalDateTime firstSearchedAt;

    // 最後一次搜尋時間
    @Column(name = "last_searched_at", nullable = false)
    private LocalDateTime lastSearchedAt;
}
