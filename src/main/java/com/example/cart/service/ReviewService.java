package com.example.cart.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cart.model.Game;
import com.example.cart.model.GameReview;
import com.example.cart.model.Member;
import com.example.cart.repository.GameRepository;
import com.example.cart.repository.GameReviewRepository;
import com.example.cart.repository.MemberGameRepository;
import com.example.cart.repository.MemberRepository;

// 遊戲評論的業務邏輯層
// 規則：只有遊戲庫裡有這款遊戲的會員能評論；每人每款遊戲可以留多則；只能改、刪自己的評論
@Service
public class ReviewService {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    private final GameReviewRepository gameReviewRepository;
    private final GameRepository gameRepository;
    private final MemberRepository memberRepository;
    private final MemberGameRepository memberGameRepository;

    // 建構子注入：Spring 會自動把各個 Repository 的實例傳進來
    public ReviewService(GameReviewRepository gameReviewRepository, GameRepository gameRepository,
            MemberRepository memberRepository, MemberGameRepository memberGameRepository) {
        this.gameReviewRepository = gameReviewRepository;
        this.gameRepository = gameRepository;
        this.memberRepository = memberRepository;
        this.memberGameRepository = memberGameRepository;
    }

    // 某款遊戲顯示中的評論
    public List<GameReview> getGameReviews(Integer gameId) {
        return gameReviewRepository.findByGame_GameIdAndStatusOrderByCreatedAtDesc(gameId, STATUS_ACTIVE);
    }

    // 新增評論：每次都新增一筆
    @Transactional
    public GameReview addReview(Integer memberId, Integer gameId, Integer rating, Boolean isRecommended,
            String content) throws Exception {
        validate(rating, isRecommended);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new Exception("找不到會員，memberId = " + memberId));
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new Exception("找不到遊戲，gameId = " + gameId));
        if (!memberGameRepository.existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(memberId, gameId)) {
            throw new Exception("擁有這款遊戲才能評論");
        }

        GameReview review = new GameReview();
        review.setMember(member);
        review.setGame(game);
        review.setRating(rating);
        review.setIsRecommended(isRecommended);
        review.setContent(content);
        review.setCreatedAt(LocalDateTime.now());
        review.setStatus(STATUS_ACTIVE);
        return gameReviewRepository.save(review);
    }

    // 修改自己的評論
    @Transactional
    public GameReview updateReview(Integer memberId, Integer reviewId, Integer rating, Boolean isRecommended,
            String content) throws Exception {
        validate(rating, isRecommended);
        GameReview review = getOwnActiveReview(memberId, reviewId);
        review.setRating(rating);
        review.setIsRecommended(isRecommended);
        review.setContent(content);
        review.setUpdatedAt(LocalDateTime.now());
        return gameReviewRepository.save(review);
    }

    // 刪除自己的評論：只把狀態改成 DELETED，不真的刪掉資料
    @Transactional
    public String deleteReview(Integer memberId, Integer reviewId) throws Exception {
        GameReview review = getOwnActiveReview(memberId, reviewId);
        review.setStatus(STATUS_DELETED);
        review.setUpdatedAt(LocalDateTime.now());
        gameReviewRepository.save(review);
        return "Review deleted successfully";
    }

    // 找出這位會員自己、而且還沒刪除的評論
    private GameReview getOwnActiveReview(Integer memberId, Integer reviewId) throws Exception {
        GameReview review = gameReviewRepository.findById(reviewId)
                .filter(r -> STATUS_ACTIVE.equals(r.getStatus()))
                .orElseThrow(() -> new Exception("找不到評論，reviewId = " + reviewId));
        if (!review.getMember().getMemberId().equals(memberId)) {
            throw new Exception("只能修改或刪除自己的評論");
        }
        return review;
    }

    // 評分必須是 1～5，是否推薦必填
    private void validate(Integer rating, Boolean isRecommended) throws Exception {
        if (rating == null || rating < 1 || rating > 5) {
            throw new Exception("評分必須是 1～5");
        }
        if (isRecommended == null) {
            throw new Exception("請選擇是否推薦");
        }
    }
}
