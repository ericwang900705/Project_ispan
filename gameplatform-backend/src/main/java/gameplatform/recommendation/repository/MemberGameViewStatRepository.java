
package gameplatform.recommendation.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.recommendation.dto.GameViewPreferenceDto;
import gameplatform.recommendation.entity.MemberGameViewStat;
import gameplatform.recommendation.entity.MemberGameViewStatId;

public interface MemberGameViewStatRepository
    extends JpaRepository<MemberGameViewStat, MemberGameViewStatId> {

  // 查詢會員最常瀏覽的遊戲，並取得遊戲名稱
  // SELECT new gameplatform.recommendation.dto.GameViewPreferenceDto
  // 這是 JPQL 的 DTO Projection（DTO 投影查詢）。
  // 它會直接將查詢結果轉成：
  // GameViewPreferenceDto
  @Query("""
      SELECT new gameplatform.recommendation.dto.GameViewPreferenceDto(
          v.gameId,
          g.gameName,
          v.viewCount,
          v.lastViewedAt
      )
      FROM MemberGameViewStat v, Game g
      WHERE v.gameId = g.gameId
        AND v.memberId = :memberId
      ORDER BY v.viewCount DESC, v.lastViewedAt DESC
      """)
  List<GameViewPreferenceDto> findTopViewPreferences(
      @Param("memberId") Integer memberId,
      Pageable pageable);
}
