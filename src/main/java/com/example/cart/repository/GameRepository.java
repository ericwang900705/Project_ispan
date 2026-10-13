package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.cart.model.Game;

// 遊戲的資料存取層（暫代版本，正式版本由隊友負責）
// 繼承 JpaRepository 就自動擁有 save()、findById()、findAll()、deleteById() 等方法
public interface GameRepository extends JpaRepository<Game, Integer> {
    // 每款遊戲的平均評分、評論數（只算沒被刪掉的評論）
    // 每一筆是 [gameId, 平均評分, 評論數]
    @Query("SELECT r.game.gameId, AVG(r.rating), COUNT(r) FROM GameReview r WHERE r.status = 'ACTIVE' GROUP BY r.game.gameId")
    List<Object[]> findRatingStats();

    // 每款遊戲賣出幾套（只算已付款的訂單）
    // 每一筆是 [gameId, 賣出數量]
    @Query("SELECT oi.game.gameId, SUM(oi.quantity) FROM OrderItem oi WHERE oi.order.orderStatus = 'PAID' GROUP BY oi.game.gameId")
    List<Object[]> findSalesStats();
}
