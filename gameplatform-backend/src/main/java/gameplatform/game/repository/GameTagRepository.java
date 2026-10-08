package gameplatform.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import gameplatform.game.entity.GameTag;
import gameplatform.game.entity.GameTagId;

public interface GameTagRepository
        extends JpaRepository<GameTag, GameTagId> {

    // 負責查詢指定遊戲的標籤關聯
    List<GameTag> findByGameId(Integer gameId);

    // 設定新標籤時，刪除舊的關聯資料
    void deleteByGameId(Integer gameId);

    // 這個方法是用來判斷某款遊戲是否已經有指定標籤
    boolean existsByGameIdAndTagId(Integer gameId, Integer tagId);

    // 這個方法是用來刪除指定遊戲與標籤的關聯
    void deleteByGameIdAndTagId(Integer gameId, Integer tagId);
}