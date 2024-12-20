package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.UnbanRequest;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.UnbanRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class UnbanRequestController  {

    private final UnbanRequestRepository unbanRequestRepository;
    private final ClientRepository clientRepository;

    @Autowired
    public UnbanRequestController(UnbanRequestRepository unbanRequestRepository, ClientRepository clientRepository) {
        this.unbanRequestRepository = unbanRequestRepository;
        this.clientRepository = clientRepository;
    }

    @PostMapping("/sendUnbanRequest")
    public String sendUnbanRequest(@RequestParam("message") String message, Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            model.addAttribute("error", "Вы не авторизованы.");
            return "banned";
        }

        String username = authentication.getName();
        Optional<Client> client = clientRepository.findByUsername(username);

        if (client.isEmpty()) {
            model.addAttribute("error", "Пользователь не найден.");
            return "banned";
        }

        UnbanRequest existingRequest = unbanRequestRepository.findByClient(client.get());
        if (existingRequest != null) {
            model.addAttribute("message", "Вы уже отправили запрос на разблокировку. Ожидайте ответа администратора.");
            model.addAttribute("formSubmitted", true);
            return "banned";
        }

        UnbanRequest unbanRequest = new UnbanRequest();
        unbanRequest.setClient(client.get());
        unbanRequest.setMessage(message);
        unbanRequest.setRequestTime(LocalDateTime.now());

        unbanRequestRepository.save(unbanRequest);

        model.addAttribute("message", "Ваш запрос на разблокировку был отправлен.");
        model.addAttribute("formSubmitted", true);
        return "banned";
    }

}
