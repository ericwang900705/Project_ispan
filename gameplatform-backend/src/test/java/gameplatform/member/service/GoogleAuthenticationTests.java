package gameplatform.member.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gameplatform.member.dto.*;
import gameplatform.member.entity.*;
import gameplatform.member.repository.*;
import gameplatform.member.exception.LoginFailedException;
import gameplatform.support.model.*;
import gameplatform.support.service.JwtService;
import com.google.api.client.googleapis.auth.oauth2.*;
import java.time.LocalDateTime;
import java.util.*;
class GoogleAuthenticationTests {
 GoogleTokenService google;MemberRepository members;MemberOAuthAccountRepository accounts;MemberVerificationRepository verifications;MemberLoginAudit audit;JwtService jwt;GoogleAccountService service;GoogleLoginRequest input;Member member;
 @BeforeEach void setup(){google=mock(GoogleTokenService.class);members=mock(MemberRepository.class);accounts=mock(MemberOAuthAccountRepository.class);verifications=mock(MemberVerificationRepository.class);audit=mock(MemberLoginAudit.class);jwt=mock(JwtService.class);service=new GoogleAccountService(google,members,accounts,verifications,audit,jwt);input=new GoogleLoginRequest();input.setIdToken("signed-google-token");input.setNonce("0123456789abcdef");member=Member.builder().memberId(7).username("aki").email("aki@example.com").build();when(google.verify(input)).thenReturn(new GoogleTokenService.Identity("sub","aki@gmail.com","Aki"));when(jwt.issue(any())).thenReturn(Map.of("accessToken","member-jwt"));}
 MemberOAuthAccount account(Member owner,String sub){var a=new MemberOAuthAccount();a.setMember(owner);a.setProvider("GOOGLE");a.setProviderUserId(sub);return a;}
 Actor actor(){return new Actor(7,"MEMBER","aki","");}
 @Test void newGoogleMemberHasVerifiedEmailAndBinding(){when(members.save(any())).thenAnswer(i->{Member m=i.getArgument(0);m.setMemberId(7);return m;});assertEquals("member-jwt",service.login(input));verify(accounts).save(argThat(a->a.getProviderUserId().equals("sub")));verify(verifications).save(argThat(v->Boolean.TRUE.equals(v.getIsVerified()) && v.getSentAt()!=null && !v.getExpiresAt().isBefore(v.getSentAt())));verify(audit).record(any(Member.class),eq("SUCCESS"));verify(jwt).issue(argThat(a->a.role().equals("MEMBER")));}
 @Test void existingEmailNeverAutomaticallyLinks(){when(members.existsByEmail("aki@gmail.com")).thenReturn(true);assertEquals(409,assertThrows(Problem.class,()->service.login(input)).httpStatus());verify(accounts,never()).save(any());verifyNoInteractions(jwt);}
 @Test void linkedSubjectWinsOverChangedEmail(){when(accounts.findByProviderAndProviderUserId("GOOGLE","sub")).thenReturn(Optional.of(account(member,"sub")));service.login(input);assertEquals("aki@example.com",member.getEmail());verify(members,never()).existsByEmail(any());}
 @Test void publisherIsStillFrontMemberNeverStaff(){member.setIsPublisher(true);when(accounts.findByProviderAndProviderUserId("GOOGLE","sub")).thenReturn(Optional.of(account(member,"sub")));service.login(input);verify(jwt).issue(argThat(a->a.role().equals("PUBLISHER")));}
 @Test void suspensionBlocksGoogleLogin(){member.setLockedUntil(LocalDateTime.now().plusDays(1));when(accounts.findByProviderAndProviderUserId("GOOGLE","sub")).thenReturn(Optional.of(account(member,"sub")));assertThrows(LoginFailedException.class,()->service.login(input));verify(audit).record(member,"LOCKED");verifyNoInteractions(jwt);}
 @Test void invalidCredentialIsAudited(){when(google.verify(input)).thenThrow(new Problem(401,"bad"));assertThrows(LoginFailedException.class,()->service.login(input));verify(audit).record(null,"FAILED");verifyNoInteractions(jwt);}
 @Test void explicitLinkUsesCurrentMember(){when(members.findByUsernameForUpdate("aki")).thenReturn(Optional.of(member));assertEquals(true,service.link(actor(),input).get("linked"));verify(accounts).save(argThat(a->a.getMember()==member));verifyNoInteractions(jwt);}
 @Test void staffCannotLink(){for(String role:List.of("ADMIN","STAFF","REVIEWER","SUPPORT"))assertEquals(403,assertThrows(Problem.class,()->service.link(new Actor(1,role,"admin",""),input)).httpStatus());verifyNoInteractions(members);}
 @Test void anotherOwnersGoogleCannotBeTaken(){when(members.findByUsernameForUpdate("aki")).thenReturn(Optional.of(member));when(accounts.findByProviderAndProviderUserId("GOOGLE","sub")).thenReturn(Optional.of(account(Member.builder().memberId(99).build(),"sub")));assertEquals(409,assertThrows(Problem.class,()->service.link(actor(),input)).httpStatus());verify(accounts,never()).save(any());}
 @Test void changingBindingIsRejected(){when(members.findByUsernameForUpdate("aki")).thenReturn(Optional.of(member));when(accounts.findByMember_MemberIdAndProvider(7,"GOOGLE")).thenReturn(Optional.of(account(member,"other")));assertThrows(Problem.class,()->service.link(actor(),input));}
 @Test void invalidLinkCredentialDoesNotExpireMemberJwt(){when(google.verify(input)).thenThrow(new Problem(401,"bad"));assertEquals(400,assertThrows(Problem.class,()->service.link(actor(),input)).httpStatus());}
 GoogleIdToken.Payload claims(){var p=new GoogleIdToken.Payload();p.setSubject("sub");p.setEmail("aki@gmail.com");p.setEmailVerified(true);p.set("nonce",input.getNonce());return p;}
 @Test void validClaimsAccepted(){assertEquals("sub",GoogleTokenService.identity(claims(),input.getNonce()).subject());}
 @Test void nonceMismatchRejected(){var p=claims();p.set("nonce","wrong");assertThrows(Problem.class,()->GoogleTokenService.identity(p,input.getNonce()));}
 @Test void unverifiedEmailRejected(){var p=claims();p.setEmailVerified(false);assertThrows(Problem.class,()->GoogleTokenService.identity(p,input.getNonce()));}
 @Test void missingSubRejected(){var p=claims();p.setSubject(null);assertThrows(Problem.class,()->GoogleTokenService.identity(p,input.getNonce()));}
 @Test void officialVerifierFailureCannotIssueJwt() throws Exception{var verifier=mock(GoogleIdTokenVerifier.class);when(verifier.verify("signed-google-token")).thenReturn(null);assertEquals(401,assertThrows(Problem.class,()->new GoogleTokenService("client",verifier).verify(input)).httpStatus());}
 @Test void missingConfigurationIsUnavailable(){assertEquals(503,assertThrows(Problem.class,()->new GoogleTokenService("",mock(GoogleIdTokenVerifier.class)).verify(input)).httpStatus());}
 @Test void verifierNetworkFailureCanBeRetried()throws Exception{var verifier=mock(GoogleIdTokenVerifier.class);when(verifier.verify("signed-google-token")).thenThrow(new java.io.IOException());assertEquals(503,assertThrows(Problem.class,()->new GoogleTokenService("client",verifier).verify(input)).httpStatus());}
}
