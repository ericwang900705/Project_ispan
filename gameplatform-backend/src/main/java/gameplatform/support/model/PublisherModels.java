package gameplatform.support.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class PublisherModels {
    private PublisherModels() {}
    public record GameInput(@NotBlank @Size(max=150) String name,
            @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal price,
            @Size(max=20000) String description, @Size(max=500) String coverUrl, LocalDateTime releaseDate) {}
    public record RequestInput(@Size(max=500) String reason) {}
    public record TagsInput(@NotNull @Size(max=50) List<@NotNull @Positive Integer> tagIds) {}
    public record BuildInput(@Positive Integer id, @NotBlank @Size(max=30) String version,
            @NotBlank @Size(max=500) String fileUrl, @PositiveOrZero Long fileSize,
            @NotNull @Pattern(regexp="ACTIVE|INACTIVE") String status) {}
    public record BuildsInput(@NotNull @Size(max=50) List<@NotNull @Valid BuildInput> builds) {}
    public record MediaInput(@NotNull @Pattern(regexp="IMAGE|VIDEO") String type,
            @NotBlank @Size(max=500) String url, @Min(1) @Max(1000) int order) {}
    public record MediaList(@NotNull @Size(max=50) List<@NotNull @Valid MediaInput> media) {}
    public record DecisionInput(@NotNull @Pattern(regexp="APPROVED|REJECTED") String decision,
            @NotBlank @Size(max=500) String comment, @NotBlank @Size(max=36) String requestKey) {}
}
