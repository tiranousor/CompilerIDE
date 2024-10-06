package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.util.ClientNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Optional<Client> getPerson(String username) {
        return clientRepository.findByUsername(username);
    }
    @Transactional
    public void save(Client client){
        client.setPassword(passwordEncoder.encode(client.getPassword()));
        clientRepository.save(client);
    }
    public Optional<Client> findByUsername(String username) {
        return clientRepository.findByUsername(username);
    }
    public Client findOne(int id) {
        Optional<Client> foundClient = clientRepository.findById(id);
        return foundClient.orElse(null);
    }
    public Optional<Client> getClient(String username) {
        return clientRepository.findByUsername(username);
    }
    public void updateResetPasswordToken(String token, String email) {
        Client client = clientRepository.findByEmail(email);
        if (client != null) {
            client.setResetPasswordToken(token);
            clientRepository.save(client);
        } else {
            throw new ClientNotFoundException("Email не найден: " + email);
        }

    }

    public Client getByResetPasswordToken(String token) {
        return clientRepository.findByResetPasswordToken(token);
    }

    public void updatePassword(Client client, String newPassword) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = passwordEncoder.encode(newPassword);
        client.setPassword(encodedPassword);

        client.setResetPasswordToken(null);
        clientRepository.save(client);
    }
//    @Transactional
//    public void update(int id, Client updateClient){
//        updateClient.setId(id);
//        clientRepository.save(updateClient);
//    }
@Transactional
public void update(int id, Client updateClient){
    Client existingClient = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException("Client not found"));

    existingClient.setUsername(updateClient.getUsername());
    existingClient.setEmail(updateClient.getEmail());
    existingClient.setGithubProfile(updateClient.getGithubProfile());
    existingClient.setAbout(updateClient.getAbout());

    if (updateClient.getPassword() != null && !updateClient.getPassword().isEmpty()) {
        existingClient.setPassword(passwordEncoder.encode(updateClient.getPassword()));
    }

    if (updateClient.getAvatarUrl() != null) {
        existingClient.setAvatarUrl(updateClient.getAvatarUrl());
    }

    clientRepository.save(existingClient);
}

}
