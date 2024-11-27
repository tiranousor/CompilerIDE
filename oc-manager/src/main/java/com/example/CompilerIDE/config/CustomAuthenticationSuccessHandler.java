package com.example.CompilerIDE.config;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import com.example.CompilerIDE.security.ClientDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final ClientRepository clientRepository;
    private final LoginTimestampRepository loginTimestampRepository;
    @Autowired
    public CustomAuthenticationSuccessHandler(ClientRepository clientRepository, LoginTimestampRepository loginTimestampRepository) {
        this.clientRepository = clientRepository;
        this.loginTimestampRepository = loginTimestampRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        ClientDetails clientDetails = (ClientDetails) authentication.getPrincipal();
        Client client = clientDetails.getClient();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String username = authentication.getName();
        Client user = clientRepository.findByUsername(username).orElse(null);
        if (user != null) {
            LoginTimestamp loginTimestamp = new LoginTimestamp();
            loginTimestamp.setClient(user);
            loginTimestamp.setLoginTime(LocalDateTime.now());
            loginTimestampRepository.save(loginTimestamp);
            System.out.println("LoginTimestamp saved for user: " + username);
        }

        if (roles.contains("ROLE_BANNED")) {
            response.sendRedirect("/banned");
        } else if (roles.contains("ROLE_ADMIN")) {
            response.sendRedirect("/admin/dashboard");
        } else if (roles.contains("ROLE_USER")) {
            response.sendRedirect("/userProfile/" + client.getId());
        } else {
            response.sendRedirect("/login?error");
        }
    }
}
