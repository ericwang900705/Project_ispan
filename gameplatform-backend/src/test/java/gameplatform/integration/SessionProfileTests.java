package gameplatform.integration;

import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.service.SessionProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionProfileTests {
 JdbcTemplate db=mock(JdbcTemplate.class);
 StaffPermissionService grants=mock(StaffPermissionService.class);
 SessionProfileService service=new SessionProfileService(db,grants);
 Actor staff=new Actor(2,"STAFF","staff_account","");
 @Test void staffShowsNameWithoutChangingLoginIdentity() {
  when(db.queryForObject(anyString(),eq(String.class),eq(2))).thenReturn("王小明");
  when(grants.forActor(staff)).thenReturn(List.of("ORDER_VIEW"));
  var profile=service.profile(staff);
  assertEquals("王小明",profile.get("displayName"));assertEquals("staff_account",profile.get("username"));
  assertEquals(List.of("ORDER_VIEW"),profile.get("permissions"));assertFalse(profile.containsKey("password"));
 }
 @Test void revokedGrantsAndChangedNameAreReloaded() {
  when(db.queryForObject(anyString(),eq(String.class),eq(2))).thenReturn("王小明","王大明");
  when(grants.forActor(staff)).thenReturn(List.of("ORDER_VIEW"),List.of());
  assertEquals(List.of("ORDER_VIEW"),service.profile(staff).get("permissions"));
  var next=service.profile(staff);assertEquals("王大明",next.get("displayName"));assertEquals(List.of(),next.get("permissions"));
 }
 @Test void missingNameFallsBackToAccount() {
  when(grants.forActor(staff)).thenReturn(List.of());assertEquals("staff_account",service.profile(staff).get("displayName"));
 }
 @Test void memberUsesExistingUsernameWithoutInventingNewSqlFields() {
  var profile=service.profile(new Actor(7,"MEMBER","小明玩家",""));
  assertEquals("小明玩家",profile.get("displayName"));assertEquals(List.of(),profile.get("permissions"));verifyNoInteractions(db,grants);
 }
}
