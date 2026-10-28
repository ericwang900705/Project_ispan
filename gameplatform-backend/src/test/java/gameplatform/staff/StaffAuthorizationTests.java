package gameplatform.staff;

import gameplatform.admin.entity.*;
import gameplatform.admin.repository.*;
import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.config.StaffPermissionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** JPA-backed policy regressions, independent of SQL Server. */
class StaffAuthorizationTests {
    final AdminRepository admins=mock(AdminRepository.class);
    final AdminPermissionRepository catalog=mock(AdminPermissionRepository.class);
    final AdminUserPermissionRepository grants=mock(AdminUserPermissionRepository.class);
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final AdminPermissionService service=new AdminPermissionService(admins,catalog,grants,db);
    final Actor highest=new Actor(1,"ADMIN","root","");
    final Actor staff=new Actor(2,"STAFF","staff","");
    final List<AdminUserPermission> rows=new ArrayList<>();
    final Map<Integer,Admin> people=new HashMap<>();

    Admin person(int id,String role,String username) {
        AdminRole r=new AdminRole();r.setRoleCode(role);
        Admin a=new Admin();a.setAdminId(id);a.setAdminRole(r);a.setAdminAccount(username);a.setStatus("ACTIVE");
        people.put(id,a);return a;
    }
    @BeforeEach void setUp() {
        person(1,"ADMIN","root");person(2,"STAFF","staff");person(3,"REVIEWER","reviewer");person(4,"SUPPORT","support");
        when(admins.findById(anyInt())).thenAnswer(i -> Optional.ofNullable(people.get(i.getArgument(0))));
        List<AdminPermission> all=new ArrayList<>();int id=1;
        for(String code:AdminPermissionService.ALL_CODES.stream().sorted().toList()) {
            AdminPermission p=new AdminPermission();p.setPermissionId(id++);p.setPermissionCode(code);all.add(p);
        }
        when(catalog.findAll()).thenReturn(all);
        when(grants.findByAdminId(anyInt())).thenAnswer(i -> rows.stream().filter(g -> g.getAdminId().equals(i.getArgument(0))).toList());
        doAnswer(i -> { ((Iterable<AdminUserPermission>)i.getArgument(0)).forEach(rows::remove);return null; }).when(grants).deleteAll(any());
        when(grants.saveAll(any())).thenAnswer(i -> { List<AdminUserPermission> saved=new ArrayList<>();
            ((Iterable<AdminUserPermission>)i.getArgument(0)).forEach(saved::add);rows.addAll(saved);return saved; });
        when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);
    }
    void grant(int id,String code) {
        AdminUserPermission row=new AdminUserPermission();row.setAdminId(id);
        row.setPermissionId(catalog.findAll().stream().filter(p -> p.getPermissionCode().equals(code)).findFirst().orElseThrow().getPermissionId());
        row.setGrantedByAdminId(1);rows.add(row);
    }
    void denied(int status,Runnable action) { assertEquals(status,assertThrows(Problem.class,action::run).httpStatus()); }
    @Test void adminHasAllFunctions() { assertEquals(15,service.forActor(highest).size()); }
    @Test void staffStartsWithoutGrants() { assertTrue(service.forActor(staff).isEmpty()); }
    @Test void staffReadsOnlyGrantedBusinessFunctions() {
        grant(2,"GAME_REVIEW");grant(2,"PERMISSION_MANAGE");assertEquals(List.of("GAME_REVIEW"),service.forActor(staff));
    }
    @Test void reviewerRetainsFixedReviewAndMemberFunctions() {
        assertEquals(Set.of("GAME_REVIEW","PUBLISHER_REVIEW","MEMBER_SUSPEND","COMMENT_REVIEW"),new HashSet<>(service.forActor(new Actor(3,"REVIEWER","reviewer",""))));
    }
    @Test void supportOnlyHandlesTickets() { assertEquals(List.of("TICKET_HANDLE"),service.forActor(new Actor(4,"SUPPORT","support",""))); }
    @Test void adminReplacesStaffPermissions() {
        grant(2,"GAME_REVIEW");service.replaceStaffPermissions(highest,2,Set.of("TICKET_HANDLE"));
        assertEquals(List.of("TICKET_HANDLE"),service.forActor(staff));assertEquals(1,rows.get(0).getGrantedByAdminId());
    }
    @Test void emptySetRevokesEverything() { grant(2,"GAME_REVIEW");service.replaceStaffPermissions(highest,2,Set.of());assertTrue(rows.isEmpty()); }
    @Test void staffCannotDelegateEvenWithOldManagementGrant() { grant(2,"PERMISSION_MANAGE");denied(403,()->service.replaceStaffPermissions(staff,2,Set.of())); }
    @Test void adminCannotGrantManagementFunctionsToStaff() { denied(400,()->service.replaceStaffPermissions(highest,2,Set.of("STAFF_MANAGE"))); }
    @Test void unknownPermissionRejectedWithoutDeletingExistingGrants() { grant(2,"GAME_REVIEW");denied(400,()->service.replaceStaffPermissions(highest,2,Set.of("NO_SUCH_PERMISSION")));assertEquals(1,rows.size()); }
    @Test void fixedRoleCannotReceiveIndividualGrants() { denied(400,()->service.replaceStaffPermissions(highest,3,Set.of("TICKET_HANDLE"))); }
    @Test void suspendedAdminCannotGrant() { people.get(1).setStatus("SUSPENDED");denied(403,()->service.replaceStaffPermissions(highest,2,Set.of())); }
    @Test void staleOrForgedActorRejected() {
        assertTrue(service.forActor(new Actor(2,"ADMIN","staff","")).isEmpty());
        assertTrue(service.forActor(new Actor(1,"ADMIN","wrong","")).isEmpty());
    }
    @Test void grantRevocationAffectsNextDecisionWithoutNewJwt() {
        grant(2,"GAME_REVIEW");assertTrue(service.forActor(staff).contains("GAME_REVIEW"));rows.clear();assertFalse(service.forActor(staff).contains("GAME_REVIEW"));
    }
    @Test void nullListRejected() { denied(400,()->service.replaceStaffPermissions(highest,2,null)); }
    @Test void unknownStaffRejected() { denied(404,()->service.replaceStaffPermissions(highest,99,Set.of())); }
    @Test void securityAdapterUsesTheSamePolicy() {
        assertEquals(AdminPermissionService.ALL_CODES,StaffPermissionService.ALL_CODES);
        assertEquals(AdminPermissionService.BUSINESS_CODES,StaffPermissionService.CODES);
        assertTrue(StaffPermissionService.options("STAFF").isEmpty());
    }
}
