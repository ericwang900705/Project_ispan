package com.example.cart.request;

// 加入購物車時前端送來的 JSON
// 例：自己買   { "gameId": 3 }
//     送禮     { "gameId": 3, "isGift": true, "recipientMemberId": 2 }
//     用優惠券 { "gameId": 3, "couponId": 1 }
public class AddItemRequest {
    private Integer gameId;            // 要加入的遊戲 ID（必填）
    private Boolean isGift;            // 是否為禮物（不填視為 false）
    private Integer recipientMemberId; // 受贈會員 ID（送禮時必填）
    private Integer couponId;          // 優惠券 ID（選填）

    public AddItemRequest() {
    }

    public Integer getGameId() {
        return gameId;
    }

    public void setGameId(Integer gameId) {
        this.gameId = gameId;
    }

    public Boolean getIsGift() {
        return isGift;
    }

    public void setIsGift(Boolean isGift) {
        this.isGift = isGift;
    }

    public Integer getRecipientMemberId() {
        return recipientMemberId;
    }

    public void setRecipientMemberId(Integer recipientMemberId) {
        this.recipientMemberId = recipientMemberId;
    }

    public Integer getCouponId() {
        return couponId;
    }

    public void setCouponId(Integer couponId) {
        this.couponId = couponId;
    }
}
