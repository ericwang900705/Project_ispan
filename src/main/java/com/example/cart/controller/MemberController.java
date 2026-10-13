package com.example.cart.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.Member;
import com.example.cart.service.MemberService;

// 會員查詢 API（送禮用，暫代版本，正式的會員 API 由隊友負責）
@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    // 建構子注入：Spring 會自動把 MemberService 的實例傳進來
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 只回傳送禮需要的欄位，不把 email 等個資給別人看
    public record MemberSummary(Integer memberId, String username) {
    }

    // 用帳號找受贈者
    // GET /api/members/lookup?username=test2
    // 回傳 { memberId, username }；找不到回 400 { message }
    @GetMapping("/lookup")
    public ResponseEntity<MemberSummary> lookup(@RequestParam String username) throws Exception {
        Member member = memberService.findMemberByUsername(username);
        return new ResponseEntity<>(new MemberSummary(member.getMemberId(), member.getUsername()), HttpStatus.OK);
    }
}
