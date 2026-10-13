package com.example.cart.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 會員遊戲庫資料表 member_games：會員擁有的遊戲，結帳後寫入
@Entity
@Table(name = "member_games")
public class MemberGame {
    // 遊戲庫紀錄編號
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer libraryId;

    // 會員編號_FK：遊戲的擁有者（送禮時是受贈者）
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    @JsonIgnore
    private Member member;

    // 遊戲編號_FK
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 來源訂單編號_FK
    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    // 取得來源途徑：PURCHASE（自己買）/ GIFT（別人送的）
    @Column(length = 20)
    private String obtainedSource;

    // 取得時間
    private LocalDateTime obtainedAt;

    // 總遊玩時長（分鐘）
    private Integer totalPlayMinutes;

    // 最後啟動時間
    private LocalDateTime lastPlayedAt;

    // 授權有效狀態
    private Boolean isActive;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public MemberGame() {
    }

    // 回傳 JSON 時只帶來源訂單編號，不帶整張訂單
    public Integer getOrderId() {
        return order == null ? null : order.getOrderId();
    }

    public Integer getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Integer libraryId) {
        this.libraryId = libraryId;
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

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getObtainedSource() {
        return obtainedSource;
    }

    public void setObtainedSource(String obtainedSource) {
        this.obtainedSource = obtainedSource;
    }

    public LocalDateTime getObtainedAt() {
        return obtainedAt;
    }

    public void setObtainedAt(LocalDateTime obtainedAt) {
        this.obtainedAt = obtainedAt;
    }

    public Integer getTotalPlayMinutes() {
        return totalPlayMinutes;
    }

    public void setTotalPlayMinutes(Integer totalPlayMinutes) {
        this.totalPlayMinutes = totalPlayMinutes;
    }

    public LocalDateTime getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(LocalDateTime lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
