package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.ClientDto;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.ProjectService;
import com.example.CompilerIDE.util.ClientValidator;
import com.example.CompilerIDE.util.FileUploadUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final ClientService clientService;
    private final ProjectService projectService;
    private final ClientValidator clientValidator;

//    @Autowired
    private PasswordEncoder passwordEncoder;
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
@PostMapping("/edit/{id}")
public String updateProfile(Authentication authentication, @PathVariable("id") int id,
                            @Valid Client clientForm, BindingResult bindingResult,
                            @RequestParam("avatarFile") MultipartFile avatarFile) {
    if (bindingResult.hasErrors()) {
        return "editProfile";
    }

    Client existingClient = clientService.findOne(id);

    if (existingClient == null) {
        return "redirect:/error";
    }

    existingClient.setUsername(clientForm.getUsername());
    existingClient.setEmail(clientForm.getEmail());
    existingClient.setGithubProfile(clientForm.getGithubProfile());
    existingClient.setAbout(clientForm.getAbout());

    if (!clientForm.getPassword().isEmpty()) {
        existingClient.setPassword(passwordEncoder.encode(clientForm.getPassword()));
    }

    if (!avatarFile.isEmpty()) {
        String fileName = StringUtils.cleanPath(avatarFile.getOriginalFilename());
        String uploadDir = "user-photos/" + existingClient.getId();

        try {
            FileUploadUtil.saveFile(uploadDir, fileName, avatarFile);
            existingClient.setAvatarUrl("/" + uploadDir + "/" + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    clientService.update(id, existingClient);

    if (!authentication.getName().equals(existingClient.getUsername())) {
        Authentication newAuth = new UsernamePasswordAuthenticationToken(existingClient.getUsername(), authentication.getCredentials(), authentication.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    return "redirect:/userProfile";
}

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



}