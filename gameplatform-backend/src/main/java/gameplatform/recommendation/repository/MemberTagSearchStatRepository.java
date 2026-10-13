
package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.recommendation.dto.TagSearchPreferenceDto;
import gameplatform.recommendation.entity.MemberTagSearchStat;
import gameplatform.recommendation.entity.MemberTagSearchStatId;

public interface MemberTagSearchStatRepository
        extends JpaRepository<MemberTagSearchStat, MemberTagSearchStatId> {

    // 查詢會員最常搜尋的 Tag，直接轉成 DTO
    @Query("""
            SELECT new gameplatform.recommendation.dto.TagSearchPreferenceDto(
                s.tagId,
                t.tagName,
                s.searchCount,
                s.lastSearchedAt
            )
            FROM MemberTagSearchStat s, Tag t
            WHERE s.tagId = t.tagId
              AND s.memberId = :memberId
            ORDER BY s.searchCount DESC, s.lastSearchedAt DESC
            """)
    List<TagSearchPreferenceDto> findTopSearchPreferences(
            @Param("memberId") Integer memberId,
            Pageable pageable);
}
