package gameplatform.support.model;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class Models {
    public record Room(int id, int memberId, String memberName, String status, Integer adminId, Integer ticketId,
            String ticketNo,
            LocalDateTime createdAt, LocalDateTime closedAt) {
    }

    public record Game(int id, String name) {}

    public record Ticket(int id, String ticketNo, int memberId, String memberName, int categoryId, String categoryName,
            Integer gameId, String gameName, String subject, String content, String status, Integer adminId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt, List<Game> games) {
        public Ticket withGames(List<Game> games) {
            return new Ticket(id, ticketNo, memberId, memberName, categoryId, categoryName, gameId, gameName,
                    subject, content, status, adminId, createdAt, updatedAt, List.copyOf(games));
        }
    }

    public record Attachment(int id, String fileName, String contentType, int sizeBytes, int width, int height) {
    }

    public record AttachmentData(String contentType, byte[] bytes) {
    }

    public record Message(int id, String senderType, String senderName, String content, LocalDateTime sentAt,
            List<Attachment> attachments) {
        public Message(int id, String senderType, String senderName, String content, LocalDateTime sentAt) {
            this(id, senderType, senderName, content, sentAt, List.of());
        }
    }

    public record MessagePage(List<Message> items, boolean hasMore) {
    }

    public record MessageInput(@NotBlank @Size(max = 4000) String content) {
    }

    public record EscalateInput(@NotNull @Positive Integer categoryId, @Positive Integer gameId,
            @Size(max = 20) List<@NotNull @Positive Integer> gameIds,
            @NotBlank @Size(max = 100) String subject,
            @NotBlank @Size(max = 8000) String summary) {
    }
}
