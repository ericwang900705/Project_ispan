package gameplatform.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.Tag;

public interface TagRepository
        extends JpaRepository<Tag, Integer> {
}