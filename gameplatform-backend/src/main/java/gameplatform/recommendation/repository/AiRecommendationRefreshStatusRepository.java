
package gameplatform.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.recommendation.entity.AiRecommendationRefreshStatus;

public interface AiRecommendationRefreshStatusRepository
                extends JpaRepository<AiRecommendationRefreshStatus, Integer> {

}
