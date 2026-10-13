
package gameplatform.recommendation.dto;

import java.time.LocalDateTime;

public record GameViewPreferenceDto(

        // 遊戲 ID
        Integer gameId,

        // 遊戲名稱
        String gameName,

        // 累積瀏覽次數
        Integer viewCount,

        // 最後一次成功計數的時間
        LocalDateTime lastViewedAt

) {
}
