
package gameplatform.recommendation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.dto.PopularGameDto;
import gameplatform.recommendation.repository.PopularGameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PopularGameService {

    private final PopularGameRepository popularGameRepository;

    /**
     * 查詢熱門遊戲 TOP 5。
     *
     * 目前依照已付款訂單的購買數量排序。
     */
    @Transactional(readOnly = true)
    public List<PopularGameDto> getTop5PopularGames() {

        return popularGameRepository.findTop5PopularGames();
    }

    /**
     * 查詢指定會員尚未擁有的熱門遊戲 TOP 5。
     *
     * 排除會員已購買及收到贈送的遊戲。
     */
    @Transactional(readOnly = true)
    public List<PopularGameDto> getTop5PopularGamesForMember(
            Integer memberId) {

        // 1. 驗證會員 ID
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("會員 ID 不正確");
        }

        // 2. 查詢會員尚未擁有的熱門遊戲
        return popularGameRepository.findTop5PopularGamesForMember(
                memberId);
    }

}
