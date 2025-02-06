package com.discobingohits.login_sockets_bingo.controller;

import com.discobingohits.login_sockets_bingo.dto.AuthRequest;
import com.discobingohits.login_sockets_bingo.dto.AuthResponse;
import com.discobingohits.login_sockets_bingo.dto.RegisterRequest;
import com.discobingohits.login_sockets_bingo.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/verify/{token}")
    public ResponseEntity<?> verifyEmail(@PathVariable String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().body(Map.of("message", "Email verificado correctamente"));
    }
}
