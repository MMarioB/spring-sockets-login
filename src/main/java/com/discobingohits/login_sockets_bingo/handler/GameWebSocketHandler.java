package com.discobingohits.login_sockets_bingo.handler;

import com.discobingohits.login_sockets_bingo.model.GameConfig;
import com.discobingohits.login_sockets_bingo.model.GameRoom;
import com.discobingohits.login_sockets_bingo.model.GameState;
import com.discobingohits.login_sockets_bingo.model.Player;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConnectionQueue connectionQueue = new ConnectionQueue();

    private class ConnectionQueue {
        private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();

        private class QueueEntry {
            final JsonNode playerData;
            final WebSocketSession session;

            QueueEntry(JsonNode playerData, WebSocketSession session) {
                this.playerData = playerData;
                this.session = session;
            }
        }

        public void enqueue(String roomCode, JsonNode playerData, WebSocketSession session) throws IOException {
            queues.computeIfAbsent(roomCode, k -> new ArrayList<>())
                    .add(new QueueEntry(playerData, session));
            processQueue(roomCode);
        }

        private void processQueue(String roomCode) throws IOException {
            List<QueueEntry> queue = queues.get(roomCode);
            if (queue == null || queue.isEmpty()) return;

            GameRoom room = gameRooms.get(roomCode);
            if (room == null) {
                queues.remove(roomCode);
                return;
            }

            while (!queue.isEmpty() && room.getPlayers().size() < room.getConfig().getMaxPlayers()) {
                QueueEntry entry = queue.remove(0);
                try {
                    Thread.sleep(500); // Delay entre conexiones
                    String playerName = entry.playerData.get("name").asText();
                    WebSocketSession playerSession = entry.session;
                    String sessionId = playerSession.getId();
                    String messageId = entry.playerData.has("messageId") ?
                            entry.playerData.get("messageId").asText() : null;

                    Player existingPlayer = findExistingPlayer(room, playerName, sessionId);
                    boolean isReconnecting = false;

                    if (existingPlayer != null) {
                        isReconnecting = true;
                        existingPlayer.setId(sessionId);
                        existingPlayer.setReconnected(true);
                        existingPlayer.setReady(room.getPhase().equals("playing"));
                    } else {
                        room.getPlayers().add(new Player(
                                sessionId,
                                playerName,
                                false,
                                false,
                                new Date()
                        ));
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("event", "roomJoined");
                    response.put("roomCode", roomCode);
                    response.put("players", room.getPlayers());
                    response.put("config", room.getConfig());
                    response.put("phase", room.getPhase());
                    response.put("currentCategory", room.getCurrentCategory());
                    response.put("gameState", room.getGameState());
                    response.put("isReconnecting", isReconnecting);
                    if (messageId != null) {
                        response.put("messageId", messageId);
                    }

                    playerSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

                    broadcastToRoom(roomCode, Map.of(
                            "event", "playersUpdate",
                            "players", room.getPlayers()
                    ));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Error en delay de cola", e);
                }
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("Cliente conectado: {}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String event = jsonNode.get("event").asText();
        JsonNode data = jsonNode.get("data");

        switch (event) {
            case "checkRoom":
                handleCheckRoom(session, data);
                break;
            case "createRoom":
                handleCreateRoom(session, data);
                break;
            case "joinRoom":
                handleJoinRoom(session, data);
                break;
            case "playerReady":
                handlePlayerReady(session, data);
                break;
            case "startGame":
                handleStartGame(session, data);
                break;
            case "selectCategory":
                handleSelectCategory(session, data);
                break;
            case "revealSong":
                handleRevealSong(session, data);
                break;
            case "enableMarking":
                handleMarkingChange(session, data, true);
                break;
            case "disableMarking":
                handleMarkingChange(session, data, false);
                break;
            case "winner":
                handleWinner(session, data);
                break;
        }
    }

    private void handleCheckRoom(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        Map<String, Object> response = new HashMap<>();
        response.put("event", "roomStatus");
        response.put("exists", room != null);

        if (messageId != null) {
            response.put("messageId", messageId);
        }

        if (room != null) {
            response.put("phase", room.getPhase());
            response.put("playerCount", room.getPlayers().size());
            response.put("maxPlayers", room.getConfig().getMaxPlayers());
        }

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        for (Map.Entry<String, GameRoom> entry : gameRooms.entrySet()) {
            GameRoom room = entry.getValue();
            String roomCode = entry.getKey();

            if (sessionId.equals(room.getHost())) {
                handleHostDisconnect(roomCode);
            } else {
                handlePlayerDisconnect(room, sessionId);
            }
        }
    }

    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    public void cleanupInactiveRooms() {
        long now = System.currentTimeMillis();
        gameRooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            long inactiveTime = now - room.getCreatedAt().getTime();
            boolean shouldRemove = inactiveTime > 3600000; // 1 hora

            if (shouldRemove) {
                log.info("Sala {} eliminada por inactividad", entry.getKey());
            }
            return shouldRemove;
        });
    }

    private void handleHostDisconnect(String roomCode) {
        try {
            broadcastToRoom(roomCode, Map.of(
                    "event", "hostDisconnected"
            ));
            gameRooms.remove(roomCode);
            log.info("Sala {} eliminada por desconexión del host", roomCode);
        } catch (IOException e) {
            log.error("Error al manejar desconexión del host", e);
        }
    }

    private void handlePlayerDisconnect(GameRoom room, String sessionId) {
        try {
            room.getPlayers().removeIf(p -> p.getId().equals(sessionId));

            if (!room.getPlayers().isEmpty()) {
                broadcastToRoom(room.getCode(), Map.of(
                        "event", "playersUpdate",
                        "players", room.getPlayers()
                ));
            }

            log.info("Jugador {} eliminado de la sala {}", sessionId, room.getCode());
        } catch (IOException e) {
            log.error("Error al manejar desconexión del jugador", e);
        }
    }

    private void handleCreateRoom(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.has("roomCode") ?
                data.get("roomCode").asText() :
                generateRoomCode();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;

        GameConfig config = objectMapper.treeToValue(data, GameConfig.class);
        GameRoom room = new GameRoom(session.getId(), config);
        room.setCode(roomCode);

        room.getPlayers().add(new Player(
                session.getId(),
                "Game Master",
                true,
                true,
                new Date()
        ));

        gameRooms.put(roomCode, room);

        Map<String, Object> response = new HashMap<>();
        response.put("event", "roomCreated");
        response.put("roomCode", roomCode);
        response.put("players", room.getPlayers());
        response.put("config", room.getConfig());
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private String generateRoomCode() {
        return Long.toString(Math.abs(System.nanoTime()), 36).substring(0, 6).toUpperCase();
    }

    private void handleJoinRoom(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null) {
            sendError(session, "Sala no encontrada", "ROOM_NOT_FOUND", messageId);
            return;
        }

        if (room.getPlayers().size() >= room.getConfig().getMaxPlayers()) {
            sendError(session, "Sala llena", "ROOM_FULL", messageId);
            return;
        }

        connectionQueue.enqueue(roomCode, data, session);
    }

    private Player findExistingPlayer(GameRoom room, String name, String id) {
        return room.getPlayers().stream()
                .filter(p -> p.getName().equals(name) && !p.getName().equals("Game Master"))
                .findFirst()
                .orElse(null);
    }

    private void handlePlayerReady(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null) {
            sendError(session, "Sala no encontrada", "ROOM_NOT_FOUND", messageId);
            return;
        }

        Player player = room.getPlayers().stream()
                .filter(p -> p.getId().equals(session.getId()))
                .findFirst()
                .orElse(null);

        if (player != null) {
            player.setReady(true);
            Map<String, Object> response = new HashMap<>();
            response.put("event", "playersUpdate");
            response.put("players", room.getPlayers());
            if (messageId != null) {
                response.put("messageId", messageId);
            }
            broadcastToRoom(roomCode, response);
        } else {
            sendError(session, "Jugador no encontrado en la sala", "PLAYER_NOT_FOUND", messageId);
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendError(session, message, null, null);
    }

    private void sendError(WebSocketSession session, String message, String code, String messageId) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("event", "error");
        error.put("message", message);
        if (code != null) {
            error.put("code", code);
        }
        if (messageId != null) {
            error.put("messageId", messageId);
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }

    private void handleStartGame(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String difficulty = data.get("difficulty").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null || !session.getId().equals(room.getHost())) {
            sendError(session, "No autorizado", "UNAUTHORIZED", messageId);
            return;
        }

        if (!room.getPlayers().stream().allMatch(p -> p.isHost() || p.isReady())) {
            sendError(session, "No todos los jugadores están listos", "PLAYERS_NOT_READY", messageId);
            return;
        }

        room.setPhase("playing");
        room.getConfig().setDifficulty(difficulty);
        room.setGameState(new GameState(difficulty, new Date(), 0));

        Map<String, Object> response = new HashMap<>();
        response.put("event", "gameStarted");
        response.put("difficulty", difficulty);
        response.put("players", room.getPlayers());
        response.put("gameState", room.getGameState());
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        broadcastToRoom(roomCode, response);
    }

    private void handleSelectCategory(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        JsonNode categoryNode = data.get("category");
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null || !session.getId().equals(room.getHost())) {
            sendError(session, "No autorizado", "UNAUTHORIZED", messageId);
            return;
        }

        room.setCurrentCategory(categoryNode.toString());
        room.setPhase("category");

        Map<String, Object> response = new HashMap<>();
        response.put("event", "categorySelected");
        response.put("category", categoryNode);
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        broadcastToRoom(roomCode, response);
    }

    private void handleRevealSong(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        JsonNode songData = data.get("songData");
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null || !session.getId().equals(room.getHost())) {
            sendError(session, "No autorizado", "UNAUTHORIZED", messageId);
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("event", "songRevealed");
        response.put("songData", songData);
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        broadcastToRoom(roomCode, response);
    }

    private void handleMarkingChange(WebSocketSession session, JsonNode data, boolean enable) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null || !session.getId().equals(room.getHost())) {
            sendError(session, "No autorizado", "UNAUTHORIZED", messageId);
            return;
        }

        room.setPhase(enable ? "marking" : "waiting");
        Map<String, Object> response = new HashMap<>();
        response.put("event", enable ? "markingEnabled" : "markingDisabled");
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        broadcastToRoom(roomCode, response);
    }

    private void handleWinner(WebSocketSession session, JsonNode data) throws IOException {
        String roomCode = data.get("roomCode").asText();
        String playerName = data.get("playerName").asText();
        String messageId = data.has("messageId") ? data.get("messageId").asText() : null;
        GameRoom room = gameRooms.get(roomCode);

        if (room == null) {
            sendError(session, "Sala no encontrada", "ROOM_NOT_FOUND", messageId);
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("event", "gameWinner");
        response.put("playerName", playerName);
        if (messageId != null) {
            response.put("messageId", messageId);
        }

        broadcastToRoom(roomCode, response);
    }

    private void broadcastToRoom(String roomCode, Map<String, Object> message) throws IOException {
        GameRoom room = gameRooms.get(roomCode);
        if (room != null) {
            String messageStr = objectMapper.writeValueAsString(message);
            for (Player player : room.getPlayers()) {
                WebSocketSession playerSession = sessions.get(player.getId());
                if (playerSession != null && playerSession.isOpen()) {
                    playerSession.sendMessage(new TextMessage(messageStr));
                }
            }
        }
    }
}