package gameplatform.game.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import gameplatform.game.dto.GameRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameAuditLog;
import gameplatform.game.entity.GameTag;
import gameplatform.game.repository.GameAuditLogRepository;
import gameplatform.game.repository.GameRepository;
import gameplatform.game.repository.TagRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    private final GameAuditLogRepository gameAuditLogRepository;
    private final TagRepository tagRepository;

    ////////////////////////////////////////////////////////////////////////////
    /// 宣告一個共用常數： 搜尋的遊戲狀態條件
    public static final List<String> PUBLIC_STATUSES = List.of(
            "COMING_SOON",
            "PUBLISHED",
            "PENDING_OFF_SHELF");

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 查詢所有遊戲(只查詢狀態為 COMING_SOON 或 PUBLISHED 的遊戲)
    public Page<Game> findAll(Pageable pageable) {

        return gameRepository.findByStatusIn(
                PUBLIC_STATUSES,
                pageable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 模糊查詢
    public List<Game> findByGameName(String keyword) {

        return gameRepository
                .findByGameNameContainingAndStatusIn(
                        keyword,
                        PUBLIC_STATUSES);
    }

    // 根據 gameId 查詢單一公開遊戲
    public Optional<Game> findById(Integer gameId) {

        return gameRepository.findByGameIdAndStatusIn(
                gameId,
                PUBLIC_STATUSES);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 新增遊戲資料邏輯
    public Game insert(GameRequest request) {

        Game game = new Game();

        game.setMemberId(request.getMemberId());
        game.setGameName(request.getGameName());
        game.setCoverUrl(request.getCoverUrl());
        game.setReleaseDate(request.getReleaseDate());
        game.setPrice(request.getPrice());
        game.setDescription(request.getDescription());

        return gameRepository.save(game);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 修改,更新遊戲資料邏輯
    public Game update(Integer gameId, GameRequest request) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game oldGame = result.get();

            // COMING_SOON 的遊戲修改發售時間時
            // 新的發售時間必須至少在 24 小時後
            if ("COMING_SOON".equals(oldGame.getStatus())) {

                if (request.getReleaseDate() == null) {
                    throw new IllegalArgumentException(
                            "即將發售的遊戲不能取消發售時間");
                }

                LocalDateTime minimumReleaseDate = LocalDateTime.now().plusHours(24);

                if (request.getReleaseDate().isBefore(minimumReleaseDate)) {
                    throw new IllegalArgumentException(
                            "遊戲發售時間必須至少設定在 24 小時後");
                }
            }

            oldGame.setGameName(request.getGameName());
            oldGame.setCoverUrl(request.getCoverUrl());
            oldGame.setReleaseDate(request.getReleaseDate());
            oldGame.setPrice(request.getPrice());
            oldGame.setDescription(request.getDescription());

            return gameRepository.save(oldGame);

        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    // 上架遊戲(直接讓資料庫一次處理所有符合下架條件的遊戲，不需要先把遊戲資料查出來，再用 Java 一筆一筆修改)
    public Game publish(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();
            game.setStatus("PUBLISHED");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 下架遊戲(直接讓資料庫一次處理所有符合下架條件的遊戲，不需要先把遊戲資料查出來，再用 Java 一筆一筆修改)
    public Game offShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();
            game.setStatus("OFF_SHELF");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 預發售邏輯
    public Game comingSoon(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isPresent()) {

            Game game = result.get();

            // 必須有設定發售時間
            if (game.getReleaseDate() == null) {
                throw new IllegalArgumentException("請先設定遊戲發售時間");
            }

            // 最早可以發售的時間 = 現在 + 24 小時
            LocalDateTime minimumReleaseDate = LocalDateTime.now().plusHours(24);

            // 發售時間少於 24 小時
            if (game.getReleaseDate().isBefore(minimumReleaseDate)) {
                throw new IllegalArgumentException(
                        "遊戲發售時間必須至少設定在 24 小時後");
            }

            game.setStatus("COMING_SOON");

            return gameRepository.save(game);

        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 自動上架遊戲(不使用for迴圈)
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void autoPublishGames() {

        int updated = gameRepository.autoPublishGames();

        if (updated > 0) {
            System.out.println(
                    "自動發售完成，本次上架 " + updated + " 款遊戲");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // 自動下架遊戲(不使用for迴圈)
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void autoOffShelfGames() {

        int updated = gameRepository.autoOffShelfGames();

        if (updated > 0) {
            System.out.println(
                    "自動下架完成，本次下架 "
                            + updated
                            + " 款遊戲");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // 發行商送出遊戲審核
    @Transactional
    public Game submitForReview(Integer gameId) {

        Game game = gameRepository.findById(gameId)
                .orElse(null);

        if (game == null) {
            return null;
        }

        if (!"DRAFT".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有草稿狀態的遊戲可以送審");
        }

        if (game.getScheduledOffShelfAt() != null) {
            throw new IllegalStateException(
                    "此遊戲已送出審核，請勿重複送出");
        }

        if (game.getReleaseDate() == null) {
            throw new IllegalStateException(
                    "請先設定遊戲發售時間");
        }

        // 狀態正式寫入資料庫，讓管理員可依 PENDING_REVIEW 查詢
        game.setStatus("PENDING_REVIEW");

        return gameRepository.save(game);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // 發行商下架邏輯
    @Transactional
    public Game requestOffShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        if (!"PUBLISHED".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有已上架的遊戲可以申請下架");
        }

        // 避免重複申請
        if (game.getScheduledOffShelfAt() != null) {
            throw new IllegalStateException(
                    "此遊戲已提出下架申請");
        }

        // 使用資料庫支援的預定下架狀態
        game.setStatus("PENDING_OFF_SHELF");
        game.setScheduledOffShelfAt(
                LocalDateTime.now().plusHours(24));

        return gameRepository.save(game);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // 取消下架
    @Transactional
    public Game cancelOffShelf(Integer gameId) {

        Optional<Game> result = gameRepository.findById(gameId);

        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        if (!"PENDING_OFF_SHELF".equals(game.getStatus())
                || game.getScheduledOffShelfAt() == null) {
            throw new IllegalStateException(
                    "此遊戲目前沒有待取消的下架申請");
        }

        // 已到期就不能取消
        if (!game.getScheduledOffShelfAt()
                .isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "取消下架期限已過");
        }

        // 取消下架，恢復已上架狀態
        game.setStatus("PUBLISHED");
        game.setScheduledOffShelfAt(null);

        return gameRepository.save(game);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // 發行商查詢自己發行的遊戲(包含分頁)
    public Page<Game> findMyGames(
            Integer memberId,
            Pageable pageable) {

        return gameRepository.findByMemberId(memberId, pageable);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // 發行商查詢自己遊戲的審核歷史
    public List<GameAuditLog> findMyGameAuditHistory(
            Integer gameId,
            Integer memberId) {

        // 1. 查詢遊戲是否存在
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到此遊戲"));

        // 2. 確認遊戲是否屬於這位發行商
        if (!game.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "你沒有權限查看此遊戲的審核紀錄");
        }

        // 3. 查詢遊戲的審核歷史
        return gameAuditLogRepository
                .findByGameIdOrderByReviewedAtDesc(gameId);
    }

    public Page<Game> findPublicGamesByTag(
            Integer tagId,
            Pageable pageable) {

        // 確認標籤存在
        if (!tagRepository.existsById(tagId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "找不到此遊戲標籤");
        }

        // 查詢公開遊戲
        return gameRepository.findPublicGamesByTagId(
                tagId,
                pageable);
    }

    // 查詢同時擁有多個標籤的公開遊戲
    public Page<Game> findPublicGamesByTags(
            List<Integer> tagIds,
            Pageable pageable) {

        // 1. 檢查是否有選擇標籤
        if (tagIds == null || tagIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "請至少選擇一個遊戲標籤");
        }

        // 2. 去除重複標籤
        List<Integer> distinctTagIds = tagIds.stream()
                .distinct()
                .toList();

        // 3. 檢查標籤是否存在
        for (Integer tagId : distinctTagIds) {

            if (tagId == null || !tagRepository.existsById(tagId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到遊戲標籤：" + tagId);
            }
        }

        // 4. 查詢同時擁有所有標籤的公開遊戲
        return gameRepository.findPublicGamesByTagIds(
                distinctTagIds,
                (long) distinctTagIds.size(),
                pageable);
    }

    // 遊戲進階搜尋
    public Page<Game> advancedSearch(
            String keyword,
            List<Integer> tagIds,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String releaseStatus,
            Boolean freeOnly,
            LocalDate releaseDateFrom,
            LocalDate releaseDateTo,
            Pageable pageable) {

        // 1. 檢查價格範圍
        if ((minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "價格不可小於 0");
        }

        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "最低價格不可大於最高價格");
        }
        // 狀態驗證
        if (releaseStatus != null
                && !releaseStatus.isBlank()
                && !releaseStatus.equals("COMING_SOON")
                && !releaseStatus.equals("PUBLISHED")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "發售狀態只能是 COMING_SOON 或 PUBLISHED");
        }
        if (Boolean.TRUE.equals(freeOnly)
                && minPrice != null
                && minPrice.compareTo(BigDecimal.ZERO) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "免費遊戲篩選不能搭配大於 0 的最低價格");
        }
        // 檢查發售日期區間
        if (releaseDateFrom != null
                && releaseDateTo != null
                && releaseDateFrom.isAfter(releaseDateTo)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "發售起始日期不可晚於結束日期");
        }

        // 2. 整理標籤
        List<Integer> selectedTags = tagIds == null
                ? List.of()
                : tagIds.stream()
                        .distinct()
                        .toList();

        // 3. 檢查標籤是否存在
        for (Integer tagId : selectedTags) {
            if (tagId == null || !tagRepository.existsById(tagId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到遊戲標籤：" + tagId);
            }
        }

        // 4. 建立動態查詢條件
        Specification<Game> spec = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // 只顯示公開遊戲，並依發售狀態篩選
            if ("COMING_SOON".equals(releaseStatus)) {

                predicates.add(
                        cb.equal(root.get("status"), "COMING_SOON"));

            } else if ("PUBLISHED".equals(releaseStatus)) {

                predicates.add(
                        root.get("status").in(
                                "PUBLISHED",
                                "PENDING_OFF_SHELF"));

            } else {

                predicates.add(
                        root.get("status").in(PUBLIC_STATUSES));
            }

            // 遊戲名稱模糊搜尋
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("gameName")),
                                "%" + keyword.trim()
                                        .toLowerCase(Locale.ROOT) + "%"));
            }

            // 最低價格
            if (minPrice != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice));
            }

            // 最高價格
            if (maxPrice != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice));
            }
            // 免費遊戲篩選
            if (Boolean.TRUE.equals(freeOnly)) {

                predicates.add(
                        cb.equal(
                                root.get("price"),
                                BigDecimal.ZERO));
            }
            // 發售日期起始日（包含當天 00:00）
            if (releaseDateFrom != null) {

                LocalDateTime startDateTime = releaseDateFrom.atStartOfDay();

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.<LocalDateTime>get("releaseDate"),
                                startDateTime));
            }

            // 發售日期結束日（包含整個結束日期）
            if (releaseDateTo != null) {

                // 使用隔天 00:00 作為不包含的上限
                LocalDateTime endExclusive = releaseDateTo.plusDays(1).atStartOfDay();

                predicates.add(
                        cb.lessThan(
                                root.<LocalDateTime>get("releaseDate"),
                                endExclusive));
            }

            // 多標籤 AND 篩選
            for (Integer tagId : selectedTags) {

                Subquery<Integer> subquery = query.subquery(Integer.class);

                Root<GameTag> gameTagRoot = subquery.from(GameTag.class);

                subquery.select(gameTagRoot.get("gameId"));

                subquery.where(
                        cb.equal(
                                gameTagRoot.get("gameId"),
                                root.get("gameId")),
                        cb.equal(
                                gameTagRoot.get("tagId"),
                                tagId));

                predicates.add(cb.exists(subquery));
            }

            return cb.and(
                    predicates.toArray(new Predicate[0]));
        };

        // 5. 執行分頁查詢
        return gameRepository.findAll(spec, pageable);
    }

}