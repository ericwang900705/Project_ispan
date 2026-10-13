package com.example.cart.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 遊戲評論資料表 game_reviews
// 同一個會員對同一款遊戲可以留多則評論
@Entity
@Table(name = "game_reviews")
public class GameReview {
    // 評論編號_PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reviewId;

    // 會員編號_FK：寫評論的人
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 遊戲編號_FK：被評論的遊戲
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 評分：1～5
    private Integer rating;

    // 是否推薦
    private Boolean isRecommended;

    // 評論內容：NVARCHAR(MAX)
    @Nationalized
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    // 評論日期
    private LocalDateTime createdAt;

    // 修改日期：沒修改過時為 null
    private LocalDateTime updatedAt;

    // 評論狀態：ACTIVE（顯示中）/ DELETED（已刪除）
    @Column(length = 20)
    private String status;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public GameReview() {
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public void setReviewId(Integer reviewId) {
        this.reviewId = reviewId;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Boolean getIsRecommended() {
        return isRecommended;
    }

    public void setIsRecommended(Boolean isRecommended) {
        this.isRecommended = isRecommended;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
