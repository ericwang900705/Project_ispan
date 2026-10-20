package gameplatform.member.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gameplatform.member.dto.*;
import gameplatform.member.entity.*;
import gameplatform.member.repository.*;
import gameplatform.support.service.JwtService;
import gameplatform.mail.service.MailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
class GoogleMemberCompatibilityTests {
 MemberRepository members=mock(MemberRepository.class);PasswordHistoryRepository passwords=mock(PasswordHistoryRepository.class);PasswordEncoder encoder=mock(PasswordEncoder.class);JwtService jwt=mock(JwtService.class);MemberVerificationRepository verifications=mock(MemberVerificationRepository.class);PasswordResetRepository resets=mock(PasswordResetRepository.class);MailService mail=mock(MailService.class);JdbcTemplate db=mock(JdbcTemplate.class);GoogleAccountService google=mock(GoogleAccountService.class);MemberLoginAudit audit=mock(MemberLoginAudit.class);
 MemberService service=new MemberService(members,passwords,encoder,jwt,verifications,resets,mail,db,google,audit);
 Member member=Member.builder().memberId(7).username("aki").email("aki@example.com").build();
 @Test void resetDoesNotLiftSuspension(){var until=LocalDateTime.now().plusDays(3);member.setLockedUntil(until);var token=PasswordReset.builder().member(member).resetToken("token").expiresAt(LocalDateTime.now().plusHours(1)).build();when(resets.findByResetToken("token")).thenReturn(Optional.of(token));when(encoder.encode(any())).thenReturn("hash");var input=new ResetPasswordRequest();input.setToken("token");input.setNewPassword("new-password");service.resetPassword(input);assertEquals(until,member.getLockedUntil());assertTrue(token.getIsReset());verify(passwords).save(any());}
 @Test void googleOnlyProfileHasNoPassword(){when(members.findByUsername("aki")).thenReturn(Optional.of(member));assertFalse(service.getMemberProfile("aki").getHasPassword());}
 @Test void normalPasswordLoginUsesExistingJwtIssuer(){when(members.findByUsername("aki")).thenReturn(Optional.of(member));when(passwords.findFirstByMemberOrderByCreatedAtDescPasswordHistoryIdDesc(member)).thenReturn(Optional.of(PasswordHistory.builder().passwordHash("hash").build()));when(encoder.matches("pw","hash")).thenReturn(true);when(jwt.issue(any())).thenReturn(Map.of("accessToken","member-jwt"));var input=new LoginRequest();input.setUsername("aki");input.setPassword("pw");assertEquals("member-jwt",service.login(input));verify(audit).record(member,"SUCCESS");}
 @Test void resetEmailUsesCurrentFrontendRoute(){when(members.findByEmail("aki@example.com")).thenReturn(Optional.of(member));org.springframework.test.util.ReflectionTestUtils.setField(service,"publicUrl","https://hero.example/");var input=new ForgotPasswordRequest();input.setEmail("aki@example.com");service.forgotPassword(input);verify(mail).sendHtmlMail(eq("aki@example.com"),any(),contains("https://hero.example/#/member/reset-password?token="));}
 @Test void pendingEmailVerificationBlocksPasswordLogin(){when(members.findByUsername("aki")).thenReturn(Optional.of(member));when(passwords.findFirstByMemberOrderByCreatedAtDescPasswordHistoryIdDesc(member)).thenReturn(Optional.of(PasswordHistory.builder().passwordHash("hash").build()));when(encoder.matches("pw","hash")).thenReturn(true);when(verifications.findByEmail(member.getEmail())).thenReturn(List.of(MemberVerification.builder().isVerified(false).build()));var input=new LoginRequest();input.setUsername("aki");input.setPassword("pw");assertThrows(gameplatform.member.exception.LoginFailedException.class,()->service.login(input));verifyNoInteractions(jwt);}
 @Test void verificationEmailUsesConfiguredPublicOrigin(){when(members.findByEmail("aki@example.com")).thenReturn(Optional.of(member));org.springframework.test.util.ReflectionTestUtils.setField(service,"publicUrl","https://hero.example/");var input=new ResendVerificationRequest();input.setEmail("aki@example.com");service.resendVerificationEmail(input);verify(mail).sendHtmlMail(eq("aki@example.com"),any(),contains("https://hero.example/#/member/verify?token="));}
}
