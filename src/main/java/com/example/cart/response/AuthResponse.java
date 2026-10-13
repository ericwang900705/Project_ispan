package com.example.cart.response;

// 註冊 / 登入的回應資料（會轉成 JSON 回傳給前端）
// 例如：{ "message": "Signup success" }
public class AuthResponse {
    private String message; // 回應訊息

    // 無參數建構子：轉 JSON 時需要使用
    public AuthResponse() {
    }

    // 全參數建構子
    public AuthResponse(String message) {
        this.message = message;
    }

    // 取得回應訊息
    public String getMessage() {
        return message;
    }

    // 設定回應訊息
    public void setMessage(String message) {
        this.message = message;
    }
}
