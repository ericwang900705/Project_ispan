package com.example.cart.model;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 遊戲資料表 game（暫代版本）
// 正式的 game 由隊友負責，這裡只放購物車 / 結帳會用到的欄位，
// 表名與欄位名稱完全照規格，之後換成隊友的版本時其他程式不用改
@Entity
@Table(name = "game")
public class Game {
    // 遊戲編號
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer gameId;

    // 遊戲名稱：NVARCHAR
    @Nationalized
    private String gameName;

    // 價錢（實際售價）：INT
    private Integer price;

    // 原價：INT，比 price 高代表有打折；NULL 代表沒打折
    private Integer originalPrice;

    // 遊戲簡介：NVARCHAR
    @Nationalized
    private String description;

    // 遊戲封面網址：VARCHAR(500)
    @Column(length = 500)
    private String coverUrl;

    // 狀態：VARCHAR(20)
    @Column(length = 20)
    private String status;

    // 遊戲類型（例如 射擊、角色扮演、策略）：NVARCHAR(50)
    @Nationalized
    @Column(length = 50)
    private String genre;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public Game() {
    }

    public Integer getGameId() {
        return gameId;
    }

    public void setGameId(Integer gameId) {
        this.gameId = gameId;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Integer originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }
}
