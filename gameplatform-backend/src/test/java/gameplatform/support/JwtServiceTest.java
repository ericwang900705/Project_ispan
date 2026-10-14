package gameplatform.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.security.oauth2.jwt.JwtException;

import gameplatform.support.model.Actor;
import gameplatform.support.service.JwtService;

class JwtServiceTest {
 static String randomKey(){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return java.util.Base64.getEncoder().encodeToString(bytes);}

    @Test
    void issueVerifyRevoke() {
        var service = new JwtService(randomKey(), "gameplatform", 900);
        String token = (String) service.issue(new Actor(12, "MEMBER", "member1", "")).get("accessToken");
        var jwt = service.decoder().decode(token);
        assertEquals(12, service.actor(jwt).id());
        assertFalse(service.actor(jwt).isAdmin());
        service.revoke(jwt);
        assertFalse(service.active(jwt));
        assertThrows(JwtException.class, () -> service.decoder().decode(token));
    }

    @Test
    void noExpiryStillVerifiesAndCanBeRevoked() {
        var service = new JwtService(randomKey(), "gameplatform", 0);
        var issued = service.issue(new Actor(12, "MEMBER", "member1", ""));
        assertEquals(0L, issued.get("expiresIn"));
        String token = (String) issued.get("accessToken");
        var jwt = service.decoder().decode(token);
        assertNull(jwt.getExpiresAt());
        assertTrue(service.active(jwt));
        service.revoke(jwt);
        assertThrows(JwtException.class, () -> service.decoder().decode(token));
    }

    @Test
    void rejectsOtherSigningKey() {
        var a = new JwtService(randomKey(), "gameplatform", 900);
        var b = new JwtService(randomKey(), "gameplatform", 900);
        String token = (String) a.issue(new Actor(1, "ADMIN", "admin1", "")).get("accessToken");
        assertThrows(JwtException.class, () -> b.decoder().decode(token));
    }

    @Test
    void rejectsMissingProductionSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", "gameplatform", 900));
    }

 @Test void staffRoleChangeInvalidatesOldTokenImmediately() {
   var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
   var service=new JwtService(randomKey(),"gameplatform",900,db);
   org.mockito.Mockito.when(db.queryForObject(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(String.class),org.mockito.ArgumentMatchers.eq(7))).thenReturn("rev1");
   String token=(String)service.issue(new Actor(7,"ADMIN","admin","")).get("accessToken");
   org.mockito.Mockito.when(db.queryForObject(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(Integer.class),org.mockito.ArgumentMatchers.eq(7),org.mockito.ArgumentMatchers.eq("ADMIN"),org.mockito.ArgumentMatchers.eq("rev1"))).thenReturn(1,0);
   assertEquals("ADMIN",service.actor(service.decoder().decode(token)).role());
   assertThrows(JwtException.class,()->service.decoder().decode(token));
 }
}
