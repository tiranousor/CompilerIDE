package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class UserStatusController {

    @Autowired
    private ClientService clientService;

    @GetMapping("/api/isBanned")
    public ResponseEntity<Map<String, Boolean>> isBanned(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of("banned", false));
        }

        String username = authentication.getName();
        Optional<Client> clientOpt = clientService.findByUsername(username);
        boolean banned = clientOpt.map(Client::isBanned).orElse(false);
        return ResponseEntity.ok(Map.of("banned", banned));
    }
}
