package com.example.cart.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.cart.model.Member;
import com.example.cart.repository.MemberRepository;

// 會員的業務邏輯層（暫代版本，正式版本由隊友負責）
@Service
public class MemberService {
    private final MemberRepository memberRepository;

    // 建構子注入：Spring 會自動把 MemberRepository 的實例傳進來
    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 依 ID 查詢會員：有資料就回傳，沒有就丟出例外
    public Member findMemberById(Integer id) throws Exception {
        Optional<Member> opt = memberRepository.findById(id);
        if (opt.isPresent()) {
            return opt.get();
        }
        throw new Exception("找不到會員，memberId = " + id);
    }

    // 依帳號查詢會員（送禮時用）：有資料就回傳，沒有就丟出例外
    public Member findMemberByUsername(String username) throws Exception {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new Exception("請輸入受贈者帳號");
        }
        return memberRepository.findByUsername(name)
                .orElseThrow(() -> new Exception("找不到帳號為「" + name + "」的會員"));
    }
}
