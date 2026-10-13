
package gameplatform.recommendation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gameplatform.recommendation.repository.MemberTagSearchStatJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberTagSearchStatService {

    private final MemberTagSearchStatJdbcRepository tagSearchJdbcRepository;

    /**
     * 記錄會員主動搜尋或選擇遊戲 Tag 的行為。
     *
     * 第一次搜尋：新增紀錄，search_count = 1
     * 30 分鐘後再次搜尋：search_count + 1
     * 30 分鐘內重複搜尋：不增加次數
     *
     * @param memberId 會員 ID
     * @param tagId    遊戲 Tag ID
     * @return true 代表本次成功計數
     *         false 代表 30 分鐘內已經計數
     */
    @Transactional
    public boolean recordTagSearch(Integer memberId, Integer tagId) {

        // 1. 檢查會員 ID
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("會員 ID 不正確");
        }

        // 2. 檢查 Tag ID
        if (tagId == null || tagId <= 0) {
            throw new IllegalArgumentException("Tag ID 不正確");
        }

        // 3. 呼叫 JDBC Repository，新增或更新搜尋統計
        return tagSearchJdbcRepository.recordTagSearch(
                memberId,
                tagId);
    }
}
