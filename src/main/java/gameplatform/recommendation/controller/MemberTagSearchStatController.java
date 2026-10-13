
package gameplatform.recommendation.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.service.MemberTagSearchStatService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class MemberTagSearchStatController {

    private final MemberTagSearchStatService tagSearchStatService;

    /**
     * 記錄會員主動搜尋或選擇 Tag 的行為。
     *
     * POST /api/tags/{tagId}/searches
     */
    @PostMapping("/{tagId}/searches")
    public ResponseEntity<Map<String, Boolean>> recordTagSearch(
            @PathVariable Integer tagId) {

        // TODO：整合 JWT 後，改成取得目前登入會員 ID
        Integer memberId = 1;

        boolean counted = tagSearchStatService.recordTagSearch(
                memberId,
                tagId);

        return ResponseEntity.ok(
                Map.of("counted", counted));
    }
}
