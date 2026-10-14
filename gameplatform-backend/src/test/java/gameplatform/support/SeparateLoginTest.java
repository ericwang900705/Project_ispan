package gameplatform.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import gameplatform.support.controller.AuthController;
import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.service.JwtService;

class SeparateLoginTest {
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    final UserDetailsService members = mock(UserDetailsService.class);
    final UserDetailsService staff = mock(UserDetailsService.class);
    final JwtService tokens = mock(JwtService.class);
    final AuthController controller = new AuthController(members,staff,passwords,tokens,mock(EventHub.class),mock(org.springframework.jdbc.core.JdbcTemplate.class));
    final AuthController.Login input = new AuthController.Login("same-name","secret");

    @Test void sameUsernameCanExistInBothRealmsWithoutPrivilegeConfusion() {
        Actor member = new Actor(1,"MEMBER","same-name",passwords.encode("secret"));
        Actor admin = new Actor(2,"ADMIN","same-name",passwords.encode("secret"));
        when(members.loadUserByUsername("same-name")).thenReturn(member);
        when(staff.loadUserByUsername("same-name")).thenReturn(admin);
        when(tokens.issue(member)).thenReturn(Map.of("accessToken","member-token"));
        when(tokens.issue(admin)).thenReturn(Map.of("accessToken","staff-token"));
        assertEquals("member-token",controller.memberLogin(input).get("accessToken"));
        verifyNoInteractions(staff);
        assertEquals("staff-token",controller.staffLogin(input).get("accessToken"));
    }
    @Test void memberEntranceRejectsEveryStaffRoleEvenIfProviderIsMisconfigured() {
        for(String role : new String[]{"ADMIN","REVIEWER","SUPPORT"}) {
            when(members.loadUserByUsername("same-name")).thenReturn(new Actor(1,role,"same-name",passwords.encode("secret")));
            assertThrows(ResponseStatusException.class,() -> controller.memberLogin(input));
        }
        verifyNoInteractions(tokens,staff);
    }
    @Test void staffEntranceRejectsMembersAndIncorrectPasswords() {
        when(staff.loadUserByUsername("same-name")).thenReturn(new Actor(1,"MEMBER","same-name",passwords.encode("secret")));
        assertThrows(ResponseStatusException.class,() -> controller.staffLogin(input));
        when(staff.loadUserByUsername("same-name")).thenReturn(new Actor(1,"SUPPORT","same-name",passwords.encode("different")));
        assertThrows(ResponseStatusException.class,() -> controller.staffLogin(input));
        verifyNoInteractions(tokens,members);
    }
    @Test void publisherUsesMemberEntranceAndCannotEnterStaffRealm() {
        Actor publisher = new Actor(3,"PUBLISHER","same-name",passwords.encode("secret"));
        when(members.loadUserByUsername("same-name")).thenReturn(publisher);
        when(tokens.issue(publisher)).thenReturn(Map.of("accessToken","publisher-token"));
        assertEquals("publisher-token",controller.memberLogin(input).get("accessToken"));
        when(staff.loadUserByUsername("same-name")).thenReturn(publisher);
        assertThrows(ResponseStatusException.class,()->controller.staffLogin(input));
    }
}
