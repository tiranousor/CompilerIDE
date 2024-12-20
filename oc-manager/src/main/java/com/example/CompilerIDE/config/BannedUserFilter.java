package com.example.CompilerIDE.config;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.ClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class BannedUserFilter extends OncePerRequestFilter {

    @Autowired
    private ClientRepository clientRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            Optional<Client> clientOpt = clientRepository.findByUsername(username);
            if (clientOpt.isPresent() && clientOpt.get().isBanned()) {
                // Инвалидация сессии
                request.getSession().invalidate();
                // Очистка контекста безопасности
                SecurityContextHolder.clearContext();
                // Перенаправление на страницу banned
                response.sendRedirect("/banned");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
