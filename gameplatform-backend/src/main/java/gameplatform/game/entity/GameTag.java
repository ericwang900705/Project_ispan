package gameplatform.game.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game_tags")
@IdClass(GameTagId.class)
@Getter
@Setter
public class GameTag {

    @Id
    @Column(name = "game_id")
    private Integer gameId;

    @Id
    @Column(name = "tag_id")
    private Integer tagId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}