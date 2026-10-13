package com.example.cart.model;

// ATM／超商代碼取號後的繳費資訊；ATM 沒有 paymentNo、超商代碼沒有 bankCode／virtualAccount
// 規格的 orders 沒有存這些的欄位，所以只放在記憶體（OrderService），伺服器重開後會消失（綠界也會另外寄給買家）
public record PaymentInfo(String paymentType, String bankCode, String virtualAccount, String paymentNo,
        String expireDate) {
}
