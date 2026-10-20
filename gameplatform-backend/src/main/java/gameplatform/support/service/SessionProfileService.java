package gameplatform.support.service;

import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.model.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Keep the login identity separate from the name shown in the interface. */
@Service
public class SessionProfileService {
 private final JdbcTemplate db;
 private final StaffPermissionService permissions;
 public SessionProfileService(JdbcTemplate db,StaffPermissionService permissions) {this.db=db;this.permissions=permissions;}
 public Map<String,Object> profile(Actor actor) {
  String name=actor.isStaff()?db.queryForObject("SELECT admin_name FROM dbo.admin WHERE admin_id=?",String.class,actor.id()):actor.username();
  String displayName=name==null||name.isBlank()?actor.username():name.trim();
  return Map.of("id",actor.id(),"role",actor.role(),"username",actor.username(),"displayName",displayName,
    "permissions",actor.isStaff()?permissions.forActor(actor):List.of(),"demo",false);
 }
}
