package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class BannedController {

    private final ClientRepository clientRepository;

    @Autowired
    public BannedController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping("/banned")
    public String bannedPage(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            Optional<Client> client = clientRepository.findByUsername(username);
            model.addAttribute("email", client.get().getEmail());
        }
        return "banned";
    }
}
