package com.example.cart.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.cart.model.Member;
import com.example.cart.repository.MemberRepository;

// 啟動時建立測試資料：註冊功能做好之前，讓購物車不用註冊就能直接使用
// CommandLineRunner：Spring Boot 啟動完成後會自動執行 run()
@Component
public class DataInitializer implements CommandLineRunner {
    private final MemberRepository memberRepository;

    // 建構子注入：Spring 會自動把需要的物件傳進來
    public DataInitializer(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(String... args) {
        // 資料庫裡已經有會員就不再建立，避免每次啟動重複新增
        if (memberRepository.count() > 0) {
            return;
        }
        // 建立兩個測試會員：test 是目前登入的人，test2 用來測試送禮
        Member test = memberRepository.save(new Member(null, "test", "test@example.com"));
        Member test2 = memberRepository.save(new Member(null, "test2", "test2@example.com"));
        System.out.println("已建立測試會員，memberId = " + test.getMemberId() + "、" + test2.getMemberId());
    }
}
