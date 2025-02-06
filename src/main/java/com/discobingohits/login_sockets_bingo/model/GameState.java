package com.discobingohits.login_sockets_bingo.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class GameState {
    private String difficulty;
    private Date startedAt;
    private int currentRound;

    public  GameState(String difficulty, Date startedAt, int currentRound) {
        this.difficulty = difficulty;
        this.startedAt = startedAt;
        this.currentRound = currentRound;
    }
}
