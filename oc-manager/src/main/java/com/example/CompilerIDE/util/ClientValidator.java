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
        if (clientService.findByUsername(client.getUsername()).isPresent()) {
            errors.rejectValue("username", "error.username", "Пользователь с таким именем уже существует");
        }
        if (clientService.getClientByEmail(client.getEmail()).isPresent()) {
            errors.rejectValue("email", "error.email", "Пользователь с таким email уже существует");
        }
        if (client.getBackgroundColor() != null && !client.getBackgroundColor().matches("^#([A-Fa-f0-9]{6})$")) {
            errors.rejectValue("backgroundColor", "Invalid.backgroundColor", "Недопустимый цвет фона");
        }

        // Проверка mainColor
        if (client.getMainColor() != null && !client.getMainColor().matches("^#([A-Fa-f0-9]{6})$")) {
            errors.rejectValue("mainColor", "Invalid.mainColor", "Недопустимый основной цвет");
        }
    }

}
