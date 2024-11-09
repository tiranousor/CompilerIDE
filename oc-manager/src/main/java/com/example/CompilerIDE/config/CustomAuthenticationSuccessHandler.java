package com.example.CompilerIDE.config;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.security.ClientDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        ClientDetails clientDetails = (ClientDetails) authentication.getPrincipal();
        Client client = clientDetails.getClient();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        if (roles.contains("ROLE_BANNED")) {
            response.sendRedirect("/banned");
        } else if (roles.contains("ROLE_ADMIN")) {
            response.sendRedirect("/admin/users");
        } else if (roles.contains("ROLE_USER")) {
            response.sendRedirect("/userProfile");
        } else {
            response.sendRedirect("/login?error");
        }
    }
}
