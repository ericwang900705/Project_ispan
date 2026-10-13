package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.GameReview;

// 遊戲評論的資料存取層
public interface GameReviewRepository extends JpaRepository<GameReview, Integer> {
    // 某款遊戲指定狀態的評論，最新的在前面
    List<GameReview> findByGame_GameIdAndStatusOrderByCreatedAtDesc(Integer gameId, String status);
}
