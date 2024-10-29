package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoginTimestampService {

    private final LoginTimestampRepository loginTimestampRepository;

    @Autowired
    public LoginTimestampService(LoginTimestampRepository loginTimestampRepository) {
        this.loginTimestampRepository = loginTimestampRepository;
    }

    public List<LoginTimestamp> findAllByClient(Client client) {
        return loginTimestampRepository.findAllByClient(client);
    }
}