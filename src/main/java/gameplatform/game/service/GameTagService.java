package gameplatform.game.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameTag;
import gameplatform.game.entity.Tag;
import gameplatform.game.repository.GameRepository;
import gameplatform.game.repository.GameTagRepository;
import gameplatform.game.repository.TagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameTagService {

    private final TagRepository tagRepository;
    private final GameTagRepository gameTagRepository;
    private final GameRepository gameRepository;

    // 1. 查詢全部標籤
    public List<Tag> findAllTags() {
        return tagRepository.findAll();
    }

    // 2. 查詢指定遊戲的標籤
    public List<Tag> findGameTags(Integer gameId) {

        // 確認遊戲存在
        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "找不到此遊戲");
        }

        List<GameTag> gameTags = gameTagRepository.findByGameId(gameId);

        List<Tag> result = new ArrayList<>();

        for (GameTag gameTag : gameTags) {

            Tag tag = tagRepository
                    .findById(gameTag.getTagId())
                    .orElse(null);

            if (tag != null) {
                result.add(tag);
            }
        }

        return result;
    }

    // 3. 發行商設定自己遊戲的標籤
    @Transactional
    public List<Tag> updateGameTags(
            Integer gameId,
            Integer memberId,
            List<Integer> tagIds) {

        // 查詢遊戲
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 確認遊戲擁有者
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限修改此遊戲的標籤");
        }

        // 確認標籤清單存在
        if (tagIds == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "請提供 tagIds");
        }

        // 去除重複標籤
        Set<Integer> uniqueTagIds = new HashSet<>(tagIds);

        // 確認每個標籤都存在
        for (Integer tagId : uniqueTagIds) {

            if (tagId == null ||
                    !tagRepository.existsById(tagId)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "標籤不存在：" + tagId);
            }
        }

        // 刪除舊標籤
        gameTagRepository.deleteByGameId(gameId);
        gameTagRepository.flush();

        // 新增標籤
        for (Integer tagId : uniqueTagIds) {

            GameTag gameTag = new GameTag();
            gameTag.setGameId(gameId);
            gameTag.setTagId(tagId);

            gameTagRepository.save(gameTag);
        }

        return findGameTags(gameId);
    }

    // 新增遊戲標籤
    @Transactional
    public List<Tag> addGameTags(
            Integer gameId,
            Integer memberId,
            List<Integer> tagIds) {

        // 1. 查詢遊戲
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 2. 確認是否為遊戲擁有者
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限修改此遊戲的標籤");
        }

        // 3. 確認有傳入標籤
        if (tagIds == null || tagIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "請提供至少一個標籤");
        }

        // 4. 去除重複的標籤 ID
        Set<Integer> uniqueTagIds = new HashSet<>(tagIds);

        // 5. 確認標籤都存在
        for (Integer tagId : uniqueTagIds) {

            if (tagId == null ||
                    !tagRepository.existsById(tagId)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "標籤不存在：" + tagId);
            }
        }

        // 6. 只新增尚未存在的標籤
        for (Integer tagId : uniqueTagIds) {

            boolean exists = gameTagRepository.existsByGameIdAndTagId(
                    gameId,
                    tagId);

            if (!exists) {

                GameTag gameTag = new GameTag();
                gameTag.setGameId(gameId);
                gameTag.setTagId(tagId);

                gameTagRepository.save(gameTag);
            }
        }

        // 7. 回傳遊戲目前所有標籤
        return findGameTags(gameId);
    }

    // 發行商自行刪除遊戲標籤
    @Transactional
    public List<Tag> deleteGameTag(
            Integer gameId,
            Integer memberId,
            Integer tagId) {

        // 1. 確認遊戲存在
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 2. 確認遊戲擁有者
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限刪除此遊戲的標籤");
        }

        // 3. 確認遊戲是否有這個標籤
        boolean exists = gameTagRepository.existsByGameIdAndTagId(
                gameId,
                tagId);

        if (!exists) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "此遊戲沒有這個標籤");
        }

        // 4. 只刪除指定標籤
        gameTagRepository.deleteByGameIdAndTagId(
                gameId,
                tagId);

        // 5. 回傳剩餘標籤
        return findGameTags(gameId);
    }
}