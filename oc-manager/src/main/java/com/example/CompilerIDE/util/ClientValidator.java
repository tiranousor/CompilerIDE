package com.example.CompilerIDE.util;


import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ClientValidator implements Validator {
    private final ClientService clientService;
    @Autowired
    public ClientValidator(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Client.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Client client = (Client) target;
        String username = client.getUsername() != null ? client.getUsername().toLowerCase() : "";
        String email = client.getEmail() != null ? client.getEmail().toLowerCase() : "";
        if (clientService.findByUsername(username).isPresent()) {
            errors.rejectValue("username", "error.username", "Пользователь с таким именем уже существует");
        }
        if (clientService.getClientByEmail(email).isPresent()) {
            errors.rejectValue("email", "error.email", "Пользователь с таким email уже существует");
        }

    }

}
