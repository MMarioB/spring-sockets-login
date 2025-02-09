package com.discobingohits.login_sockets_bingo.service;

import com.discobingohits.login_sockets_bingo.dto.AuthRequest;
import com.discobingohits.login_sockets_bingo.dto.AuthResponse;
import com.discobingohits.login_sockets_bingo.dto.RegisterRequest;
import com.discobingohits.login_sockets_bingo.model.User;
import com.discobingohits.login_sockets_bingo.repository.UserRepository;
import com.discobingohits.login_sockets_bingo.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setVerificationToken(UUID.randomUUID().toString());
        user.setEnabled(false);

        userRepository.save(user);

        // Enviar email de verificación
        String verificationLink = frontendUrl + "/verify/" + user.getVerificationToken();
        emailService.sendVerificationEmail(
                user.getEmail(),
                "Verifica tu cuenta en Bingo",
                "Por favor, verifica tu cuenta haciendo click en el siguiente enlace: " + verificationLink
        );

        // No devolvemos token hasta que verifique el email
        AuthResponse response = new AuthResponse();
        response.setMessage("Por favor, verifica tu email para completar el registro");
        return response;
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token inválido"));

        user.setEnabled(true);
        user.setVerificationToken(null);
        userRepository.save(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!user.isEnabled()) {
            throw new RuntimeException("Por favor verifica tu email antes de iniciar sesión");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        String token = jwtService.generateToken(user.getUsername());

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        return response;
    }
}