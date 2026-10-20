
package gameplatform.recommendation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.repository.MemberGameViewStatJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberGameViewStatService {

    private final MemberGameViewStatJdbcRepository viewStatJdbcRepository;

    /**
     * 記錄會員瀏覽遊戲詳情頁的行為。
     *
     * 第一次瀏覽：新增紀錄，view_count = 1
     * 30 分鐘後再次瀏覽：view_count + 1
     * 30 分鐘內重複瀏覽：不增加次數
     *
     * return true 代表本次成功計數
     * false 代表 30 分鐘內已計數
     */
    @Transactional
    public boolean recordGameView(Integer memberId, Integer gameId) {

        // 1. 檢查參數
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("會員 ID 不正確");
        }

        if (gameId == null || gameId <= 0) {
            throw new IllegalArgumentException("遊戲 ID 不正確");
        }

        // 2. 交給 JDBC Repository 執行原子新增或更新
        return viewStatJdbcRepository.recordView(memberId, gameId);
    }
}
