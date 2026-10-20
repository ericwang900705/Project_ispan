package gameplatform.admin;

import gameplatform.admin.service.AdminMemberService;
import gameplatform.admin.service.AdminPublisherService;
import gameplatform.mail.service.MailService;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Business regressions without a running SQL Server or mail server. */
class AdminManagementTests {
    static class FakeDb extends JdbcTemplate {
        List<Integer> pending = List.of(99);
        List<String> writes = new ArrayList<>();
        String select;
        Object[] parameters;
        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            return type.cast(1L);
        }
        @Override public List<Map<String,Object>> queryForList(String sql, Object... args) {
            select = sql; parameters = args;
            return List.of(Map.of("memberId", 10, "username", "player"));
        }
        @Override public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.contains("SELECT member_id")) return List.of(type.cast(10));
            return pending.stream().map(type::cast).toList();
        }
        @Override public int update(String sql, Object... args) {
            writes.add(sql + " " + Arrays.toString(args)); return 1;
        }
    }
    private final FakeDb db = new FakeDb();
    private final MemberRepository repo = mock(MemberRepository.class);
    private final MailService mail = mock(MailService.class);
    private final AdminMemberService members = new AdminMemberService(repo, db);
    private final AdminPublisherService publishers = new AdminPublisherService(repo, mail, db);
    private final Actor reviewer = new Actor(2, "SUPPORT", "reviewer", "");

    @Test void suspendRejectsZeroNegativeAndExcessiveHoursBeforeSaving() {
        for (int hours : List.of(-1, 0, 87601))
            assertEquals(400, assertThrows(Problem.class, () -> members.suspendMember(10, hours)).httpStatus());
        verifyNoInteractions(repo);
    }
    @Test void suspendAndRestoreUpdateLockAndResetFailureCounter() {
        Member member = Member.builder().memberId(10).username("player").failedLoginCount(3).build();
        when(repo.findById(10)).thenReturn(Optional.of(member));
        members.suspendMember(10, 24);
        assertTrue(member.getLockedUntil().isAfter(LocalDateTime.now().plusHours(23)));
        members.reactivateMember(10);
        assertNull(member.getLockedUntil()); assertEquals(0, member.getFailedLoginCount());
        verify(repo, times(2)).save(member);
    }
    @Test void missingMemberReturns404() {
        when(repo.findById(10)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(Problem.class, () -> members.reactivateMember(10)).httpStatus());
        verify(repo, never()).save(any());
    }
    @Test void searchEscapesWildcardsAndDoesNotProjectSensitiveFields() {
        var result = members.search("%_[", "LOCKED", "PUBLISHER", 1, 10);
        assertEquals(1, result.totalElements());
        assertTrue(db.select.contains("locked_until > SYSDATETIME()"));
        assertFalse(db.select.contains("national_id")); assertFalse(db.select.contains("bank_account"));
        assertFalse(db.select.contains("password"));
        assertEquals("%~%~_~[%", db.parameters[0]);
        assertEquals(10, db.parameters[db.parameters.length - 2]);
    }
    @Test void invalidPaginationAndFiltersAreRejected() {
        assertThrows(Problem.class, () -> members.search("", "", "", -1, 10));
        assertThrows(Problem.class, () -> members.search("", "", "", 0, 101));
        assertThrows(Problem.class, () -> members.search("", "UNKNOWN", "", 0, 10));
    }
    @Test void staleApplicationCannotReviewANewerSubmission() {
        assertEquals(409, assertThrows(Problem.class,
            () -> publishers.reviewApplication(10, true, reviewer, 98, "")).httpStatus());
        assertTrue(db.writes.isEmpty()); verifyNoInteractions(repo, mail);
    }
    @Test void duplicateReviewReturnsConflict() {
        db.pending = List.of();
        assertEquals(409, assertThrows(Problem.class,
            () -> publishers.reviewApplication(10, true, reviewer, 99, "")).httpStatus());
        assertTrue(db.writes.isEmpty()); verifyNoInteractions(repo, mail);
    }
    @Test void rejectionNeedsReasonAndOversizedCommentIsRejected() {
        assertEquals(400, assertThrows(Problem.class,
            () -> publishers.reviewApplication(10, false, reviewer, 99, "  ")).httpStatus());
        assertEquals(400, assertThrows(Problem.class,
            () -> publishers.reviewApplication(10, true, reviewer, 99, "a".repeat(501))).httpStatus());
        assertTrue(db.writes.isEmpty()); verifyNoInteractions(repo, mail);
    }
    @Test void rejectionPreservesReasonAndQueuesMailAfterTransactionCommit() {
        Member member = Member.builder().memberId(10).username("player").email("player@example.com")
            .companyName("Company").taxId("12345678").bankName("Bank").bankAccount("account").build();
        when(repo.findById(10)).thenReturn(Optional.of(member));
        TransactionSynchronizationManager.initSynchronization();
        try {
            publishers.reviewApplication(10, false, reviewer, 99, "請補資料");
            assertNull(member.getCompanyName()); assertNull(member.getBankAccount());
            assertTrue(db.writes.getFirst().contains("請補資料"));
            verifyNoInteractions(mail);
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
