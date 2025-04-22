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
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Optional<Client> client = clientRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier);
        if (client.isEmpty()) {
            throw new UsernameNotFoundException("User not found with identifier: " + identifier);
        }
        return new ClientDetails(client.get());
    }
}
