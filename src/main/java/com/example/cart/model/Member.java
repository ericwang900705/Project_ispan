package com.example.cart.model;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 會員資料表 members（暫代版本）
// 正式的 members 由隊友負責，這裡只放購物車 / 結帳會用到的欄位，
// 表名與欄位名稱完全照規格，之後換成隊友的版本時其他程式不用改
@Entity
@Table(name = "members")
public class Member {
    // 會員編號：INT IDENTITY(1,1)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer memberId;

    // 帳號：NVARCHAR(30)
    @Nationalized
    @Column(length = 30)
    private String username;

    // 信箱：VARCHAR(100)
    @Column(length = 100)
    private String email;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public Member() {
    }

    public Member(Integer memberId, String username, String email) {
        this.memberId = memberId;
        this.username = username;
        this.email = email;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
