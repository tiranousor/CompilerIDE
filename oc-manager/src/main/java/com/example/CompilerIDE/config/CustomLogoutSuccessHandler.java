package com.example.CompilerIDE.config;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final LoginTimestampRepository loginTimestampRepository;
    private final ClientRepository clientRepository;

    @Autowired
    public CustomLogoutSuccessHandler(LoginTimestampRepository loginTimestampRepository, ClientRepository clientRepository) {
        this.loginTimestampRepository = loginTimestampRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        if (authentication != null) {
            String username = authentication.getName();
            Client user = clientRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Найти последнюю запись входа для пользователя
            LoginTimestamp lastLogin = loginTimestampRepository.findFirstByClientAndLogoutTimeIsNullOrderByLoginTimeDesc(user);
            if (lastLogin != null) {
                lastLogin.setLogoutTime(LocalDateTime.now());
                loginTimestampRepository.save(lastLogin);
            }
        }
        response.sendRedirect("/login");
    }
}
