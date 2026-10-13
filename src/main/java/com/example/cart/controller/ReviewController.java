package com.example.cart.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.GameReview;
import com.example.cart.model.Member;
import com.example.cart.service.ReviewService;

// 遊戲評論 API
// 登入功能做好之前，先用網址上的 memberId 代表目前的會員
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    // 建構子注入：Spring 會自動把 ReviewService 的實例傳進來
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // 新增 / 修改評論的 Body
    public record ReviewRequest(Integer rating, Boolean recommended, String content) {
    }

    // 回傳給前端的評論：只帶作者帳號，不帶整個會員物件
    // mine：是不是目前會員自己寫的（前端用來顯示「修改 / 刪除」按鈕）
    public record ReviewResponse(Integer id, Integer gameId, String username, Integer rating, Boolean recommended,
            String content, LocalDateTime createdAt, LocalDateTime updatedAt, boolean mine) {
        static ReviewResponse of(GameReview review, Integer currentMemberId) {
            Member author = review.getMember();
            return new ReviewResponse(review.getReviewId(), review.getGame().getGameId(), author.getUsername(),
                    review.getRating(), review.getIsRecommended(), review.getContent(), review.getCreatedAt(),
                    review.getUpdatedAt(), Objects.equals(author.getMemberId(), currentMemberId));
        }
    }

    // 查詢某款遊戲的評論
    // GET /api/reviews/game/{gameId}?memberId=1（memberId 可省略，只影響 mine）
    @GetMapping("/game/{gameId}")
    public ResponseEntity<List<ReviewResponse>> getGameReviews(@PathVariable Integer gameId,
            @RequestParam(required = false) Integer memberId) {
        List<ReviewResponse> reviews = reviewService.getGameReviews(gameId).stream()
                .map(r -> ReviewResponse.of(r, memberId))
                .toList();
        return new ResponseEntity<>(reviews, HttpStatus.OK);
    }

    // 新增評論
    // POST /api/reviews/{memberId}/game/{gameId}
    // Body：{ rating, recommended, content }
    @PostMapping("/{memberId}/game/{gameId}")
    public ResponseEntity<ReviewResponse> addReview(@PathVariable Integer memberId, @PathVariable Integer gameId,
            @RequestBody ReviewRequest req) throws Exception {
        GameReview review = reviewService.addReview(memberId, gameId, req.rating(), req.recommended(),
                req.content());
        return new ResponseEntity<>(ReviewResponse.of(review, memberId), HttpStatus.CREATED);
    }

    // 修改自己的評論
    // PUT /api/reviews/{memberId}/{reviewId}
    // Body：{ rating, recommended, content }
    @PutMapping("/{memberId}/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(@PathVariable Integer memberId,
            @PathVariable Integer reviewId, @RequestBody ReviewRequest req) throws Exception {
        GameReview review = reviewService.updateReview(memberId, reviewId, req.rating(), req.recommended(),
                req.content());
        return new ResponseEntity<>(ReviewResponse.of(review, memberId), HttpStatus.OK);
    }

    // 刪除自己的評論
    // DELETE /api/reviews/{memberId}/{reviewId}
    @DeleteMapping("/{memberId}/{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable Integer memberId, @PathVariable Integer reviewId)
            throws Exception {
        return new ResponseEntity<>(reviewService.deleteReview(memberId, reviewId), HttpStatus.OK);
    }
}
