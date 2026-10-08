package gameplatform.support.integration;
import gameplatform.support.model.Actor;
import jakarta.servlet.http.HttpServletRequest;
/** Implement using the host's verified authentication context, never request role/id parameters. */
@FunctionalInterface
public interface CurrentActorProvider { Actor currentActor(HttpServletRequest request); }
