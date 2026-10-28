package gameplatform.integration;

import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import gameplatform.support.model.Actor;
import gameplatform.support.service.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filter-chain regression with a separately configured temporary database. */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@Transactional
class LiveIdentityTests {
 @Autowired WebApplicationContext context;
 @Autowired MemberRepository members;
 @Autowired JwtService jwt;
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 Member member(boolean publisher){String name="review_"+UUID.randomUUID().toString().substring(0,12);return members.saveAndFlush(Member.builder().username(name).email(name+"@hero.example").isPublisher(publisher).build());}
 String token(Member m){return (String)jwt.issue(new Actor(m.getMemberId(),Boolean.TRUE.equals(m.getIsPublisher())?"PUBLISHER":"MEMBER",m.getUsername(),"")).get("accessToken");}
 @Test void activeMemberReceivesDisplayNameAndEmptyStaffGrants() throws Exception {
  var m=member(false);mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token(m)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value(m.getUsername())).andExpect(jsonPath("$.permissions").isEmpty());
 }
 @Test void suspendedMemberCannotUsePreviouslyIssuedTokenOnLegacyProfile() throws Exception {
  var m=member(false);String token=token(m);m.setLockedUntil(LocalDateTime.now().plusDays(1));members.saveAndFlush(m);
  mvc.perform(get("/api/members/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void changedPublisherRoleInvalidatesExistingToken() throws Exception {
  var m=member(true);String token=token(m);m.setIsPublisher(false);members.saveAndFlush(m);
  mvc.perform(get("/api/members/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void anotherMembersPreferenceIsForbiddenThroughRealSecurityChain() throws Exception {
  var m=member(false);mvc.perform(get("/api/recommendations/members/"+(m.getMemberId()+1000)+"/profile").header("Authorization","Bearer "+token(m))).andExpect(status().isForbidden());
 }
}
