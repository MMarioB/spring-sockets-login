package com.discobingohits.login_sockets_bingo.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameConfig {
    private String roomCode;
    private String difficulty;
    private int maxPlayers = 12;
}
