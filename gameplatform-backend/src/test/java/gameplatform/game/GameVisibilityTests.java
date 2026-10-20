package gameplatform.game;

import gameplatform.game.entity.Game;
import gameplatform.game.repository.GameRepository;
import gameplatform.game.service.AdminGameService;
import gameplatform.game.service.GameService;
import gameplatform.game.service.GameTagService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import gameplatform.workspace.AdminWorkspaceController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameVisibilityTests {
    private final GameRepository games = mock(GameRepository.class);
    private final GameTagService tags = mock(GameTagService.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);

    @Test void adminDefaultListingUsesTheFilteredQuery() {
        var pageable = PageRequest.of(0, 10);
        Page<Game> result = new PageImpl<>(List.of());
        when(games.searchAdminGames(null, null, pageable)).thenReturn(result);
        assertSame(result, new AdminGameService(games).findAll(pageable));
        verify(games, never()).findAll(pageable);
    }

    @Test void adminCannotReadADraftByGuessingItsId() {
        Game draft = new Game(); draft.setGameId(10); draft.setStatus("DRAFT");
        when(games.findById(10)).thenReturn(Optional.of(draft));
        var controller = new AdminWorkspaceController(games, tags, db);
        Problem error = assertThrows(Problem.class,
            () -> controller.detail(new Actor(1, "ADMIN", "owner", ""), 10));
        assertEquals(404, error.httpStatus());
        verifyNoInteractions(tags, db);
    }

    @Test void publicListUsesOnlyPublishedStatuses() {
        var service = new GameService(games, null, null);
        var pageable = PageRequest.of(0, 10);
        service.findAll(pageable);
        verify(games).findByStatusIn(List.of("COMING_SOON", "PUBLISHED", "PENDING_OFF_SHELF"), pageable);
        verify(games, never()).findAll(pageable);
    }

    @Test void publicSearchAndDetailsUseTheSameVisibilityRule() {
        var service = new GameService(games, null, null);
        service.findByGameName("draft title");
        service.findById(10);
        verify(games).findByGameNameContainingAndStatusIn("draft title", GameService.PUBLIC_STATUSES);
        verify(games).findByGameIdAndStatusIn(10, GameService.PUBLIC_STATUSES);
        assertFalse(GameService.PUBLIC_STATUSES.contains("DRAFT"));
        assertFalse(GameService.PUBLIC_STATUSES.contains("PENDING_REVIEW"));
        verify(games, never()).findById(anyInt());
    }
}
