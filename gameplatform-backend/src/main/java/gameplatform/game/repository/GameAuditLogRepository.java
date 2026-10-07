package gameplatform.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.GameAuditLog;

public interface GameAuditLogRepository
        extends JpaRepository<GameAuditLog, Integer> {

}