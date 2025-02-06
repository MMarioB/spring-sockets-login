package com.discobingohits.login_sockets_bingo.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class GameRoom {
    private String code;
    private String host;
    private List<Player> players;
    private GameConfig config;
    private String currentCategory;
    private String phase;
    private Date createdAt;
    private GameState gameState;

    public GameRoom(String host, GameConfig config) {
        this.code = generateCode();
        this.host = host;
        this.players = new ArrayList<>();
        this.config = config;
        this.phase = "waiting";
        this.createdAt = new Date();
    }

    private String generateCode() {
        return Long.toString(Math.abs(System.nanoTime()), 36).substring(0, 6).toUpperCase();
    }
}
