
package gameplatform.recommendation.dto;

import java.time.LocalDateTime;

public record TagSearchPreferenceDto(

        // Tag ID
        Integer tagId,

        // Tag 名稱
        String tagName,

        // 累積搜尋次數
        Integer searchCount,

        // 最後一次成功計數的時間
        LocalDateTime lastSearchedAt

) {
}
