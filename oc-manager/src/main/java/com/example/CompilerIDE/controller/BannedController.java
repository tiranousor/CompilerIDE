package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.UnbanRequest;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.UnbanRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class BannedController {

    private final ClientRepository clientRepository;
    private final UnbanRequestRepository unbanRequestRepository;
    @Autowired
    public BannedController(ClientRepository clientRepository, UnbanRequestRepository unbanRequestRepository) {
        this.clientRepository = clientRepository;
        this.unbanRequestRepository = unbanRequestRepository;
    }

    @GetMapping("/banned")
    public String bannedPage(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            Optional<Client> client = clientRepository.findByUsername(username);
            client.ifPresent(value -> model.addAttribute("email", value.getEmail()));
        }
        String username = authentication.getName();
        Optional<Client> client = clientRepository.findByUsername(username);

        if (client.isPresent()) {
            UnbanRequest existingRequest = unbanRequestRepository.findByClient(client.get());
            if (existingRequest != null) {
                model.addAttribute("message", "Вы уже отправили запрос на разблокировку. Ожидайте ответа администратора.");
                model.addAttribute("formSubmitted", true);
            } else {
                model.addAttribute("formSubmitted", false);
            }
            model.addAttribute("email", client.get().getEmail());
        } else {
            model.addAttribute("error", "Пользователь не найден.");
        }
        return "banned";
    }
}
