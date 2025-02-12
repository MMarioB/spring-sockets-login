package com.discobingohits.login_sockets_bingo.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Player {
    private String id;
    private String name;
    private boolean isHost;
    private boolean ready;
    private Date joinedAt;
    private boolean reconnected;
    private String phase;

    public Player(String id, String name, boolean isHost, boolean ready, Date joinedAt) {
        this.id = id;
        this.name = name;
        this.isHost = isHost;
        this.ready = ready;
        this.joinedAt = joinedAt;
        this.phase = "waiting";
    }
}