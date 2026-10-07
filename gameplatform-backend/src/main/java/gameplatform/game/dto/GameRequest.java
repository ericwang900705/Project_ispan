package gameplatform.game.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Getter
@Setter
public class GameRequest {

    private Integer memberId;

    @NotBlank(message = "遊戲名稱不能為空")
    private String gameName;

    private String coverUrl;

    @FutureOrPresent(message = "發售時間不能早於現在")
    private LocalDateTime releaseDate;

    @PositiveOrZero(message = "遊戲價格不能小於 0")
    private BigDecimal price;

    private String description;
}