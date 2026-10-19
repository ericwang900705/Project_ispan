
package gameplatform.recommendation.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import gameplatform.recommendation.entity.AiGameRecommendation;
import gameplatform.recommendation.repository.AiGameRecommendationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRecommendationCacheService {

    private final AiGameRecommendationRepository recommendationRepository;

    // 推薦快取有效期限：7 天
    private static final int CACHE_DAYS = 7;

    public boolean needsRefresh(Integer memberId) {

        Optional<AiGameRecommendation> latest = recommendationRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(
                        memberId);

        // 第一次使用，沒有推薦資料
        if (latest.isEmpty()) {
            return true;
        }

        LocalDateTime createdAt = latest.get().getCreatedAt();

        // 建立時間異常時，視為需要更新
        if (createdAt == null) {
            return true;
        }

        // 推薦建立時間 + 7 天
        LocalDateTime expiresAt = createdAt.plusDays(CACHE_DAYS);

        // 現在是否已經到達過期時間
        return !LocalDateTime.now().isBefore(expiresAt);
    }
}
