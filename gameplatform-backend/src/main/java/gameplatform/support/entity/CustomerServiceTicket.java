package gameplatform.support.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "customer_service_tickets", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_cst_ticket_no", columnNames = "ticket_no"),
        @UniqueConstraint(name = "UQ_cst_owner", columnNames = { "ticket_id", "member_id" })
})
public class CustomerServiceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    // 外部案件編號，由 Service 取 sequence 產生；不是 IDENTITY 主鍵。
    @Column(name = "ticket_no", nullable = false, length = 30)
    private String ticketNo;

    // 等組員提供會員 Entity 後，再改為 @ManyToOne + @JoinColumn。
    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CustomerServiceCategory category;

    @Column(name = "game_id")
    private Integer gameId;

    // 跨模組遊戲仍使用 gameId；目前 Service 使用 JdbcTemplate 寫入所有關聯。
    @jakarta.persistence.OneToMany(mappedBy = "ticket", fetch = FetchType.LAZY)
    @jakarta.persistence.OrderBy("sortOrder ASC")
    private java.util.List<CustomerServiceTicketGame> ticketGames = new java.util.ArrayList<>();

    // admin 表包含管理者、審核人員與客服人員。
    @Column(name = "admin_id")
    private Integer adminId;

    @Column(name = "subject", nullable = false, columnDefinition = "nvarchar(100)")
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime2")
    private LocalDateTime updatedAt;

    @PrePersist
    private void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "OPEN";
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
