package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.util.ClientNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ClientService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public ClientService(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void save(Client client){
        client.setPassword(passwordEncoder.encode(client.getPassword()));
        clientRepository.save(client);
    }

    public List<Client> findByUsernameContainingIgnoreCase(String username) {
        return clientRepository.findByUsernameContainingIgnoreCase(username);
    }

    public Optional<Client> getClientByEmail(String email) {
        return clientRepository.findByEmail(email);
    }

    public Optional<Client> findByUsername(String username) {
        return clientRepository.findByUsername(username);
    }
    public List<Client> searchByUsername(String username) {
        Client probe = new Client();
        probe.setUsername(username);

        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnorePaths("id", "email", "githubProfile", "about", "avatarUrl") // игнорируем поля, не участвующие в поиске
                .withMatcher("username", match -> match.contains().ignoreCase());

        Example<Client> example = Example.of(probe, matcher);
        return clientRepository.findAll(example);
    }
    public Client findOne(long id) {
        Optional<Client> foundClient = clientRepository.findById(id);
        return foundClient.orElse(null);
    }

    public void updateResetPasswordToken(String token, String email) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new ClientNotFoundException("Email не найден: " + email));

        client.setResetPasswordToken(token);
        clientRepository.save(client);
    }

    public Client getByResetPasswordToken(String token) {
        return clientRepository.findByResetPasswordToken(token);
    }

    public void updatePassword(Client client, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        client.setPassword(encodedPassword);

        client.setResetPasswordToken(null);
        clientRepository.save(client);
    }

    @Transactional
    public void update(long id, Client updateClient){
        updateClient.setId(id);
        clientRepository.save(updateClient);
    }

    public boolean existsByUsername(String username) {
        return clientRepository.findByUsername(username).isPresent();
    }
}
