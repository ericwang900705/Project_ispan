package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.MemberGame;

// 會員遊戲庫的資料存取層
public interface MemberGameRepository extends JpaRepository<MemberGame, Integer> {
    // 某會員的遊戲庫，最近取得的在前面
    List<MemberGame> findByMember_MemberIdOrderByObtainedAtDesc(Integer memberId);

    // 會員是否已擁有這款遊戲（授權有效的才算）
    boolean existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(Integer memberId, Integer gameId);
}
