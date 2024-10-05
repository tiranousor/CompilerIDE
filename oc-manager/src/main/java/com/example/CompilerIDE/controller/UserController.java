package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.ClientDto;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.ProjectService;
import com.example.CompilerIDE.util.ClientValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final ClientService clientService;
    private final ProjectService projectService;
    private final ClientValidator clientValidator;
    @Value("${upload.path}")
    private String uploadPath;
    @GetMapping("/")
    public String home() {
        return "Compiler";
    }
    @GetMapping("/login")
    public String showLoginPage() {
        return "loginAndRegistration";
    }

    @GetMapping("/registration")
    public String registration(@ModelAttribute Client client){
        return "registrationPage";
    }

    @PostMapping("/process_registration")
    public String registrationPerson(@Valid @ModelAttribute Client client, BindingResult bindingResult) {
        clientValidator.validate(client, bindingResult);

        if (bindingResult.hasErrors())
            return "registrationPage";

       clientService.save(client);
        return "redirect:/login?registration";
    }


    @GetMapping("/userProfile")
    public String ClientProfile(Model model, Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("Client not found with username: " + authentication.getName()));

        List<Project> project = projectService.findByClient(client);
            model.addAttribute("client", client);
            model.addAttribute("project", project);

        return "userProfile";
    }

    @GetMapping("/edit/{id}")
    public String editProfile(Model model, @PathVariable("id") int id) {
        model.addAttribute("client", clientService.findOne(id));
        return "editProfile";
    }
//    @PostMapping("/edit/{id}")
//    public String updateProfile(Authentication authentication, @PathVariable("id") int id, @Valid Client client, BindingResult bindingResult) {
//        if (bindingResult.hasErrors()) {
//            return "editProfile";
//        }
//
//        clientService.update(id, client);
//        if (!authentication.getName().equals(client.getUsername())) {
//            Authentication newAuth = new UsernamePasswordAuthenticationToken(client.getUsername(), authentication.getCredentials(), authentication.getAuthorities());
//            SecurityContextHolder.getContext().setAuthentication(newAuth);
//        }
//
//        return "redirect:/userProfile";
//    }
//    @PostMapping("/edit/{id}")
//    public String updateProfile(Authentication authentication, @PathVariable("id") int id,@ModelAttribute("client") @Valid ClientDto clientDto, BindingResult bindingResult) {
//        if (bindingResult.hasErrors()) {
//            return "editProfile";
//        }
//        Client client = clientService.findOne(id);
//        client = clientMapper.updateUserFromDto(clientDto,client);
//        clientService.update(client);
//        if (!authentication.getName().equals(client.getUsername())) {
//            Authentication newAuth = new UsernamePasswordAuthenticationToken(client.getUsername(), authentication.getCredentials(), authentication.getAuthorities());
//            SecurityContextHolder.getContext().setAuthentication(newAuth);
//        }
//
//        return "redirect:/userProfile";
//    }


    @PostMapping("/edit/{id}")
    public String updateProfile(
            Authentication authentication,
            @PathVariable("id") int id,
            @Valid @ModelAttribute("client") Client client,
            BindingResult bindingResult,
            @RequestParam("avatar") MultipartFile avatar,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "editProfile";
        }

        Client existingClient = clientService.findOne(id);
        if (existingClient == null) {
            bindingResult.rejectValue("id", "error.client", "Client not found");
            return "editProfile";
        }

        // Обработка загрузки файла
        if (avatar != null && !avatar.isEmpty()) {
            // Проверка типа файла
            String contentType = avatar.getContentType();
            if (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/gif")) {
                // Создание директории, если она не существует
                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }

                // Генерация уникального имени файла
                String originalFilename = StringUtils.cleanPath(avatar.getOriginalFilename());
                String fileExtension = "";

                int dotIndex = originalFilename.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileExtension = originalFilename.substring(dotIndex);
                }

                String newFileName = "user_" + id + "_" + System.currentTimeMillis() + fileExtension;

                try {
                    Path path = Paths.get(uploadPath + newFileName);
                    Files.copy(avatar.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                    // Удаление старого файла, если он существует
                    if (existingClient.getAvatarUrl() != null && !existingClient.getAvatarUrl().isEmpty()) {
                        Path oldPath = Paths.get("../src/main/resources/static" + existingClient.getAvatarUrl());
                        try {
                            Files.deleteIfExists(oldPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // Сохранение пути к новому файлу
                    existingClient.setAvatarUrl("/uploads/" + newFileName);

                } catch (IOException e) {
                    e.printStackTrace();
                    model.addAttribute("error", "Ошибка при загрузке файла.");
                    return "editProfile";
                }

            } else {
                model.addAttribute("error", "Допустимы только изображения (JPEG, PNG, GIF).");
                return "editProfile";
            }
        }

        // Обновление остальных полей
        existingClient.setUsername(client.getUsername());
        existingClient.setEmail(client.getEmail());
        existingClient.setGithubProfile(client.getGithubProfile());
        existingClient.setAbout(client.getAbout());

        // Если пароль не пустой, обновить его
        if (client.getPassword() != null && !client.getPassword().isEmpty()) {
            existingClient.setPassword(client.getPassword()); // Убедитесь, что пароль хешируется
        }

        clientService.update(id, existingClient);

        // Обновление аутентификации, если имя пользователя изменилось
        if (!authentication.getName().equals(existingClient.getUsername())) {
            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    existingClient.getUsername(),
                    authentication.getCredentials(),
                    authentication.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }

        return "redirect:/userProfile";
    }

}
