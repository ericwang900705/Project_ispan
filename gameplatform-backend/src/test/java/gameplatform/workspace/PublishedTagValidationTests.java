package gameplatform.workspace;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import gameplatform.game.service.GameTagService;
import gameplatform.support.model.*;
import jakarta.persistence.EntityManager;
class PublishedTagValidationTests {
 @Test void emptyPublishedTagsRejectedBeforeChangesOrCooldown(){var db=mock(JdbcTemplate.class);var tags=mock(GameTagService.class);when(db.queryForList(contains("WHERE game_id=? AND member_id=?"),eq(1),eq(7))).thenReturn(List.of(Map.of("status","PUBLISHED")));var service=new PublisherWorkspaceService(db,null,tags,null,mock(EntityManager.class),null);assertEquals(400,assertThrows(Problem.class,()->service.saveTags(new Actor(7,"PUBLISHER","audit",""),1,new PublisherWorkspaceService.Tags(List.of()))).httpStatus());verifyNoInteractions(tags);verify(db,never()).update(anyString(),anyInt());}
}
