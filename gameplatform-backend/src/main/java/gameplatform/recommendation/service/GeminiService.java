package gameplatform.recommendation.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import gameplatform.recommendation.dto.MemberPreferenceProfileDto;
import gameplatform.recommendation.dto.RecommendationCandidateDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GeminiService {

        private final RestClient restClient;
        private final String model;

        public GeminiService(
                        @Value("${gemini.api.key}") String apiKey,
                        @Value("${gemini.model}") String model) {

                this.model = model;

                this.restClient = RestClient.builder()
                                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                                .defaultHeader("x-goog-api-key", apiKey)
                                .build();
        }

        public String testConnection() {

                Map<String, Object> request = Map.of(
                                "contents", List.of(
                                                Map.of(
                                                                "parts", List.of(
                                                                                Map.of(
                                                                                                "text",
                                                                                                "請用繁體中文簡短介紹遊戲推薦系統。")))),
                                "generationConfig", Map.of(
                                                "maxOutputTokens", 300));

                return restClient.post()
                                .uri("/models/{model}:generateContent", model)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(request)
                                .retrieve()
                                .body(String.class);
        }

        public String generateRecommendations(
                        MemberPreferenceProfileDto profile,
                        List<RecommendationCandidateDto> candidates) {

                if (candidates == null || candidates.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "沒有可推薦的候選遊戲");
                }

                // 使用 Jackson 將 DTO 轉成 JSON
                ObjectMapper objectMapper = new ObjectMapper();

                try {
                        // 為了減少傳給 AI 的資料，只保留必要欄位

                        Map<String, Object> preferences = Map.of(

                                        // 1. 會員購買過的遊戲
                                        "purchasedGames",
                                        profile.purchasedGames().stream()
                                                        .map(g -> Map.of(
                                                                        "gameId", g.gameId(),
                                                                        "gameName", g.gameName()))
                                                        .toList(),

                                        // 2. 購買遊戲的類型偏好
                                        "tagPreferences",
                                        profile.tagPreferences(),

                                        // 3. 遊玩時數偏好
                                        "playPreferences",
                                        profile.playPreferences(),

                                        // 4. 遊戲評論偏好
                                        "reviewPreferences",
                                        profile.reviewPreferences().stream()
                                                        .map(r -> Map.of(
                                                                        "gameId", r.gameId(),
                                                                        "rating", r.rating(),
                                                                        "isRecommended", r.isRecommended()))
                                                        .toList(),

                                        // 5. 遊戲瀏覽偏好
                                        "viewPreferences",
                                        profile.viewPreferences().stream()
                                                        .map(v -> Map.of(
                                                                        "gameId", v.gameId(),
                                                                        "gameName", v.gameName(),
                                                                        "viewCount", v.viewCount()))
                                                        .toList(),

                                        // 6. Tag 搜尋偏好
                                        "searchPreferences",
                                        profile.searchPreferences().stream()
                                                        .map(s -> Map.of(
                                                                        "tagId", s.tagId(),
                                                                        "tagName", s.tagName(),
                                                                        "searchCount", s.searchCount()))
                                                        .toList());

                        String profileJson = objectMapper.writeValueAsString(preferences);

                        String candidatesJson = objectMapper.writeValueAsString(candidates);

                        String prompt = """
                                        你是赫洛遊戲社交平台的 AI 遊戲推薦助手。

                                        請根據玩家的六種偏好資料，
                                        分析玩家可能喜歡的遊戲，
                                        並從候選遊戲清單中挑選最適合的遊戲。

                                        【玩家偏好資料】
                                        %s

                                        【候選遊戲】
                                        %s

                                        玩家偏好分析原則：
                                        1. purchasedGames 代表玩家已購買的遊戲。
                                        2. tagPreferences 代表玩家購買過的遊戲類型及購買次數。
                                        3. playPreferences 代表玩家的遊玩時數，遊玩越久通常代表興趣越高。
                                        4. reviewPreferences 代表玩家的評價，低評分或不推薦屬於負面訊號。
                                        5. viewPreferences 代表玩家主動瀏覽過的遊戲，瀏覽次數越多可能代表興趣越高。
                                        6. searchPreferences 代表玩家主動搜尋過的遊戲類型，搜尋次數越多可能代表興趣越高。
                                        7. 購買紀錄、長時間遊玩及明確評價，通常比單次瀏覽或搜尋更有參考價值。
                                        8. 不要將瀏覽或搜尋視為玩家已經購買遊戲。
                                        9. 若不同偏好互相矛盾，請綜合判斷，不要只依靠單一紀錄。
                                        10. 如果玩家資料不足，不要憑空假設玩家喜好。

                                        推薦規則：
                                        1. 只能推薦候選遊戲清單中的遊戲。
                                        2. 最多推薦 5 款，不可重複推薦相同遊戲。
                                        3. 依照玩家喜好排序，最適合的排在最前面。
                                        4. 每款遊戲給予 0 到 1 的推薦分數。
                                        5. 推薦原因使用繁體中文，50 字以內。
                                        6. 不要推薦玩家已經購買的遊戲。
                                        7. 候選遊戲資料不足時，不要虛構遊戲特色。
                                        8. 不要輸出 Markdown。
                                        9. 必須回傳合法 JSON。
                                        10. 不要輸出 JSON 以外的其他文字。

                                        回傳格式：
                                        {
                                          "recommendations": [
                                            {
                                              "gameId": 8,
                                              "score": 0.92,
                                              "reason": "符合玩家偏好的遊戲類型"
                                            }
                                          ]
                                        }
                                        """.formatted(profileJson, candidatesJson);

                        Map<String, Object> request = Map.of(
                                        "contents", List.of(
                                                        Map.of(
                                                                        "parts", List.of(
                                                                                        Map.of("text", prompt)))),
                                        "generationConfig", Map.of(
                                                        "responseMimeType", "application/json",
                                                        "maxOutputTokens", 3000,
                                                        "temperature", 0.3));

                        String response = restClient.post()
                                        .uri("/models/{model}:generateContent", model)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(String.class);

                        // 解析 Gemini API 回傳內容
                        JsonNode root = objectMapper.readTree(response);

                        JsonNode candidatesNode = root.path("candidates");

                        if (!candidatesNode.isArray()
                                        || candidatesNode.isEmpty()) {
                                throw new IllegalStateException(
                                                "Gemini 沒有回傳推薦結果");
                        }

                        JsonNode resultNode = candidatesNode.get(0);

                        String finishReason = resultNode.path("finishReason").stringValue();

                        if (!"STOP".equals(finishReason)) {
                                throw new IllegalStateException(
                                                "Gemini 回覆未正常完成：" + finishReason);
                        }

                        String result = resultNode
                                        .path("content")
                                        .path("parts")
                                        .get(0)
                                        .path("text")
                                        .stringValue();

                        // 確認 AI 回傳的是合法 JSON
                        JsonNode recommendationJson = objectMapper.readTree(result);

                        if (!recommendationJson
                                        .path("recommendations").isArray()) {
                                throw new IllegalStateException(
                                                "Gemini 回傳格式不正確");
                        }

                        return recommendationJson.toString();

                } catch (tools.jackson.core.JacksonException e) {
                        throw new IllegalStateException(
                                        "Gemini JSON 解析失敗", e);
                }
        }

}