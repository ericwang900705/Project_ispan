package gameplatform.game.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game_builds")
@Getter
@Setter
public class GameBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "build_id")
    private Integer buildId;

    @Column(name = "game_id", nullable = false)
    private Integer gameId;

    @Column(name = "version", nullable = false, length = 30)
    private String version;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status", nullable = false, length = 20, insertable = false)
    private String status;

    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime uploadedAt;
}