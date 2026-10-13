package gameplatform.recommendation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gameplatform.recommendation.entity.AiGameRecommendation;

public interface AiGameRecommendationRepository extends JpaRepository<AiGameRecommendation, Integer> {

        // 查詢某會員的推薦遊戲，依推薦分數由高到低排序
        List<AiGameRecommendation> findByMemberIdOrderByRecommendationScoreDesc(
                        Integer memberId);

        // 查詢某會員是否已經有某款遊戲的推薦紀錄
        Optional<AiGameRecommendation> findByMemberIdAndGameId(
                        Integer memberId,
                        Integer gameId);

        // 刪除某會員的全部推薦紀錄
        long deleteByMemberId(Integer memberId);

        @Modifying
        @Query("""
                        DELETE FROM AiGameRecommendation r
                        WHERE r.memberId = :memberId
                        """)
        int deleteAllByMemberIdBatch(@Param("memberId") Integer memberId);

        // 查詢某會員最新的推薦紀錄
        Optional<AiGameRecommendation> findFirstByMemberIdOrderByCreatedAtDesc(Integer memberId);
}