package gameplatform.support.service;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import gameplatform.support.model.Actor;

/** Member and publisher identities share the front login, never the staff realm. */
public class FrontAccountService implements UserDetailsService {
 private final JdbcTemplate db;
 public FrontAccountService(JdbcTemplate db){this.db=db;}
 @Override public UserDetails loadUserByUsername(String name){
  // Reuse the supplied SQL schema's current BCrypt password and publisher flag.
  var actors=db.query("SELECT m.member_id,m.username,m.is_publisher,m.locked_until,p.password_hash FROM members m JOIN password_history p ON p.member_id=m.member_id WHERE m.username=? AND p.password_history_id=(SELECT TOP 1 h.password_history_id FROM password_history h WHERE h.member_id=m.member_id ORDER BY h.created_at DESC,h.password_history_id DESC)",(rs,n)->{
   var until=rs.getTimestamp("locked_until");
   if(until!=null && until.toLocalDateTime().isAfter(LocalDateTime.now()))throw new UsernameNotFoundException("Account locked");
   return new Actor(rs.getInt("member_id"),rs.getBoolean("is_publisher")?"PUBLISHER":"MEMBER",rs.getString("username"),rs.getString("password_hash"));
  },name);
  if(actors.size()!=1)throw new UsernameNotFoundException("Unknown front account");
  // Only accounts with pending verification must verify; pre-existing imported
  // accounts without verification records remain usable.
  int verified = db.queryForObject("SELECT COUNT(*) FROM member_verifications v JOIN members m ON m.email=v.email WHERE m.username=? AND v.is_verified=1", Integer.class, name);
  int pending = db.queryForObject("SELECT COUNT(*) FROM member_verifications v JOIN members m ON m.email=v.email WHERE m.username=?", Integer.class, name);
  if(pending>0 && verified==0)throw new UsernameNotFoundException("Email unverified");
  return actors.get(0);
 }
}
