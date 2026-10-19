package gameplatform.staff;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import gameplatform.support.model.*;
import gameplatform.support.config.StaffPermissionService;
@Service
public class StaffService {
 private final JdbcTemplate db;private final PasswordEncoder passwords;
 public StaffService(JdbcTemplate db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;}
 public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,50}") String username,@NotBlank @Size(max=50) String name,@NotBlank @Email @Size(max=100) String email,@NotBlank @Size(min=12,max=72) String password){}
 private void lock(){Integer result=db.queryForObject("DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource='HERO_STAFF_ADMIN',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;SELECT @r",Integer.class);if(result==null||result<0)throw new Problem(409,"人員管理忙碌，請稍後再試");}
 private void admin(Actor a){
  if(a==null || !a.isSuperAdmin())throw new Problem(403,"只有最高管理員可以分派後台權限");
  Integer count=db.queryForObject("SELECT COUNT(*) FROM dbo.admin a JOIN dbo.admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=? AND a.admin_account=? AND a.status='ACTIVE' AND r.role_code='SUPERADMIN'",Integer.class,a.id(),a.username());
  if(count==null||count!=1)throw new Problem(403,"最高管理員權限已變更，請重新登入");
 }
 private int role(String code){
  db.update("INSERT INTO admin_roles(role_code,role_name,description) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM admin_roles WITH(UPDLOCK,HOLDLOCK) WHERE role_code=?)",code,Map.of("ADMIN","管理員","REVIEWER","審核人員","SUPPORT","客服人員","PENDING","待授權").get(code),"HERO 後台角色",code);
  return db.queryForObject("SELECT admin_role_id FROM admin_roles WHERE role_code=?",Integer.class,code);
 }
 private void insert(Register r,String role){
  if(r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new Problem(400,"密碼不可超過 72 bytes");
  if(db.queryForObject("SELECT COUNT(*) FROM admin WHERE admin_account=? OR email=?",Integer.class,r.username(),r.email())>0)throw new Problem(409,"後台帳號或信箱已被使用");
  db.update("INSERT INTO admin(admin_role_id,admin_account,admin_name,email,password_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",role(role),r.username(),r.name(),r.email(),passwords.encode(r.password()));
 }
 @Transactional public void register(Register r){lock();insert(r,"PENDING");}
 public List<Map<String,Object>> list(Actor a){
  admin(a);
  var rows=db.queryForList("SELECT a.admin_id AS id,a.admin_account AS username,a.admin_name AS name,a.email,a.status,r.role_code AS role FROM admin a JOIN admin_roles r ON r.admin_role_id=a.admin_role_id ORDER BY a.admin_id");
  for(var row:rows) row.put("permissions", db.queryForList("SELECT p.permission_code FROM dbo.admin_user_permissions u JOIN dbo.admin_permissions p ON p.permission_id=u.permission_id WHERE u.admin_id=? ORDER BY p.permission_code",String.class,((Number)row.get("id")).intValue()));
  return rows;
 }
 @Transactional public void change(Actor a,int id,String code){
  admin(a);if(code==null || !Set.of("PENDING","REVIEWER","SUPPORT","ADMIN").contains(code))throw new Problem(400,"不支援的後台權限");lock();
  admin(a);
  // Recheck caller under the same transaction lock.
  var target=db.queryForList("SELECT r.role_code,a.status FROM admin a JOIN admin_roles r ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=?",id);if(target.isEmpty())throw new Problem(404,"找不到後台人員");
  if("SUPERADMIN".equals(target.get(0).get("role_code")))throw new Problem(409,"最高管理員帳號受保護，不能變更角色");
  if(code.equals(target.get(0).get("role_code")))return;
  db.update("UPDATE admin SET admin_role_id=?,updated_at=SYSDATETIME() WHERE admin_id=?",role(code),id);
  // Role changes clear grants. The SUPERADMIN explicitly selects each new grant.
  db.update("DELETE FROM dbo.admin_user_permissions WHERE admin_id=?",id);

 }
 private void grant(int id,String permission,int callerId){
  int affected=db.update("INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) SELECT ?,permission_id,? FROM dbo.admin_permissions WHERE permission_code=?",id,callerId,permission);
  if(affected!=1)throw new Problem(500,"權限代碼尚未初始化，請先執行升級 SQL");
 }
 @Transactional public void permissions(Actor a,int id,List<String> requested){
  admin(a);
  if(requested==null||requested.stream().anyMatch(p->p==null||!StaffPermissionService.CODES.contains(p)))throw new Problem(400,"包含不支援的權限");
  lock();
  admin(a);
  var roles=db.queryForList("SELECT r.role_code FROM dbo.admin x JOIN dbo.admin_roles r ON x.admin_role_id=r.admin_role_id WHERE x.admin_id=?",String.class,id);
  if(roles.isEmpty())throw new Problem(404,"找不到人員");
  String role=roles.get(0);
  if("SUPERADMIN".equals(role))throw new Problem(409,"最高管理員擁有完整權限，不能修改個別權限");
  if(!StaffPermissionService.options(role).containsAll(requested))throw new Problem(400,"此人員角色不能使用指定的權限");
  db.update("DELETE FROM dbo.admin_user_permissions WHERE admin_id=?",id);
  for(String permission:new HashSet<>(requested))grant(id,permission,a.id());
 }

}
