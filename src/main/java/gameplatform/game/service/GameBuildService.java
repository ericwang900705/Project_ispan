package gameplatform.game.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import gameplatform.game.dto.GameBuildRequest;
import gameplatform.game.entity.Game;
import gameplatform.game.entity.GameBuild;
import gameplatform.game.repository.GameBuildRepository;
import gameplatform.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameBuildService {

    private final GameRepository gameRepository;
    private final GameBuildRepository gameBuildRepository;

    // 發行商新增遊戲版本
    public GameBuild insert(
            Integer gameId,
            GameBuildRequest request) {

        Optional<Game> result = gameRepository.findById(gameId);

        // 遊戲不存在
        if (result.isEmpty()) {
            return null;
        }

        Game game = result.get();

        // 只有草稿可以新增 Build
        if (!"DRAFT".equals(game.getStatus())) {
            throw new IllegalStateException(
                    "只有草稿狀態的遊戲可以新增遊戲版本");
        }

        GameBuild build = new GameBuild();

        build.setGameId(gameId);
        build.setVersion(request.getVersion());
        build.setFileUrl(request.getFileUrl());
        build.setFileSize(request.getFileSize());

        return gameBuildRepository.save(build);
    }
}