
package gameplatform.recommendation.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gameplatform.recommendation.dto.GeminiRecommendationResponseDto;
import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import gameplatform.recommendation.service.AiGameRecommendationService;
import gameplatform.recommendation.service.AiRecommendationSaveService;
import gameplatform.recommendation.service.GeminiService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/ai/gemini")
@RequiredArgsConstructor
public class GeminiTestController {

    private final GeminiService geminiService;
    private final AiGameRecommendationService recommendationService;
    private final AiRecommendationSaveService saveService;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public String testGemini() {

        return geminiService.testConnection();
    }

    // 呼叫gemini取得遊戲推薦結果
    @PostMapping(value = "/recommend/{memberId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String recommendGames(
            @PathVariable Integer memberId) {

        // 1. 查詢會員完整偏好
        MemberPreferenceProfileDto profile = recommendationService
                .getMemberPreferenceProfile(memberId);

        // 2. 查詢最多 10 款候選遊戲
        List<RecommendationCandidateDto> candidates = recommendationService
                .findCandidates(
                        memberId,
                        PageRequest.of(0, 10))
                .getContent();

        // 3. 沒有候選遊戲就不呼叫 AI
        if (candidates.isEmpty()) {
            return "{\"recommendations\":[]}";
        }

        // 4. 呼叫 Gemini 分析
        return geminiService.generateRecommendations(
                profile,
                candidates);
    }

    @PostMapping("/recommend/{memberId}/save")
    public Map<String, Object> recommendAndSave(
            @PathVariable Integer memberId) {

        // 1. 取得會員偏好
        MemberPreferenceProfileDto profile = recommendationService
                .getMemberPreferenceProfile(memberId);

        // 2. 取得候選遊戲
        List<RecommendationCandidateDto> candidates = recommendationService
                .findCandidates(
                        memberId,
                        PageRequest.of(0, 10))
                .getContent();

        if (candidates.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "沒有可推薦的候選遊戲");
        }

        // 3. 呼叫 Gemini
        String aiJson = geminiService.generateRecommendations(
                profile,
                candidates);

        // 4. JSON 轉 DTO
        GeminiRecommendationResponseDto response = objectMapper.readValue(
                aiJson,
                GeminiRecommendationResponseDto.class);

        // 5. 驗證並儲存 SQL Server
        int savedCount = saveService.saveRecommendations(
                memberId,
                response,
                candidates,
                "gemini-3.5-flash-lite");

        return Map.of(
                "success", true,
                "memberId", memberId,
                "savedCount", savedCount,
                "message", "AI 推薦結果已成功儲存");
    }
}
