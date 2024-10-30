package com.example.CompilerIDE.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ClientDto {

    @NotBlank(message = "Username is mandatory")
    private String username;
    @Email(message = "Please enter a valid email address")
    @NotBlank(message = "Email is mandatory")
    private String email;
    private String about;
    private String githubProfile;
    private String avatar_url;

}
