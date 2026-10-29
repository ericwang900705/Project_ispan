package gameplatform.member.service;

import gameplatform.member.dto.GoogleLoginRequest;
import gameplatform.member.entity.*;
import gameplatform.member.repository.*;
import gameplatform.member.exception.LoginFailedException;
import gameplatform.support.model.*;
import gameplatform.support.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private final GoogleTokenService google;
    private final MemberRepository members;
    private final MemberOAuthAccountRepository accounts;
    private final MemberVerificationRepository verifications;
    private final MemberLoginAudit audit;
    private final JwtService jwt;

    @Transactional(noRollbackFor = LoginFailedException.class)
    public String login(GoogleLoginRequest input) {
        GoogleTokenService.Identity identity;
        try {
            identity = google.verify(input);
        } catch (Problem e) {
            if (e.httpStatus() == 401) {
                audit.record(null, "FAILED");
                throw new LoginFailedException(e.getMessage());
            }
            throw e;
        }
        var account = accounts.findByProviderAndProviderUserId("GOOGLE", identity.subject());
        Member member;
        if (account.isPresent())
            member = account.get().getMember();
        else {
            if (members.existsByEmail(identity.email()))
                throw new Problem(409, "此信箱已有會員帳號，請先用原帳號登入，再到個人資料綁定 Google");
            String name = identity.name().replaceAll("[^A-Za-z0-9_]", "");
            if (name.isBlank())
                name = "Player";
            name = name.substring(0, Math.min(12, name.length()));
            member = members.save(Member.builder()
                    .username(name + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .email(identity.email()).build());
            var binding = new MemberOAuthAccount();
            binding.setMember(member);
            binding.setProvider("GOOGLE");
            binding.setProviderUserId(identity.subject());
            binding.setEmail(identity.email());
            accounts.save(binding);
            var verified = new MemberVerification();
            verified.setEmail(member.getEmail());
            verified.setVerificationToken(UUID.randomUUID().toString());
            verified.setIsVerified(true);
            var verifiedAt = LocalDateTime.now();
            verified.setSentAt(verifiedAt);
            verified.setVerifiedAt(verifiedAt);
            verified.setExpiresAt(verifiedAt.plusHours(1));
            verifications.save(verified);
        }
        if (member.getLockedUntil() != null && member.getLockedUntil().isAfter(LocalDateTime.now())) {
            audit.record(member, "LOCKED");
            throw new LoginFailedException(AccountLockMessage.until(member.getLockedUntil()));
        }
        member.setFailedLoginCount(0);
        members.save(member);
        audit.record(member, "SUCCESS");
        // Google is exclusively a front-member identity; never resolve or issue an
        // admin identity.
        return (String) jwt.issue(new Actor(member.getMemberId(),
                Boolean.TRUE.equals(member.getIsPublisher()) ? "PUBLISHER" : "MEMBER", member.getUsername(), ""))
                .get("accessToken");
    }

    private void require(Actor actor) {
        if (actor == null || !actor.isMember())
            throw new Problem(403, "僅前臺會員可綁定 Google");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(Actor actor) {
        require(actor);
        return accounts.findByMember_MemberIdAndProvider(actor.id(), "GOOGLE")
                .<Map<String, Object>>map(a -> Map.of("linked", true, "email", Objects.toString(a.getEmail(), "")))
                .orElseGet(() -> Map.of("linked", false, "email", ""));
    }

    @Transactional
    public Map<String, Object> link(Actor actor, GoogleLoginRequest input) {
        require(actor);
        GoogleTokenService.Identity identity;
        try {
            identity = google.verify(input);
        } catch (Problem e) {
            if (e.httpStatus() == 401)
                throw new Problem(400, e.getMessage());
            throw e;
        }
        var member = members.findByUsernameForUpdate(actor.username()).orElseThrow(() -> new Problem(401, "請重新登入"));
        if (!member.getMemberId().equals(actor.id()))
            throw new Problem(401, "請重新登入");
        if (member.getLockedUntil() != null && member.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new Problem(403, "帳號已被停權或鎖定");
        var existing = accounts.findByProviderAndProviderUserId("GOOGLE", identity.subject());
        if (existing.isPresent() && !existing.get().getMember().getMemberId().equals(actor.id()))
            throw new Problem(409, "此 Google 帳號已綁定其他會員");
        var own = accounts.findByMember_MemberIdAndProvider(actor.id(), "GOOGLE");
        if (own.isPresent() && !own.get().getProviderUserId().equals(identity.subject()))
            throw new Problem(409, "此會員已綁定其他 Google 帳號");
        if (own.isEmpty()) {
            var binding = new MemberOAuthAccount();
            binding.setMember(member);
            binding.setProvider("GOOGLE");
            binding.setProviderUserId(identity.subject());
            binding.setEmail(identity.email());
            accounts.save(binding);
        }
        return Map.of("linked", true, "email", identity.email(), "message", "Google 帳號綁定成功");
    }
}
