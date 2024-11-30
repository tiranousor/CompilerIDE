package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.security.ClientDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ClientDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(ClientDetailsService.class);
    private final ClientRepository clientRepository;

    public ClientDetailsService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading user by username: {}", username);
        Optional<Client> client = clientRepository.findByUsername(username);
        if (client.isEmpty()) {
            logger.error("User not found: {}", username);
            throw new UsernameNotFoundException("User not found");
        }
        logger.debug("User found: {}", username);
        return new ClientDetails(client.get());
    }
}
