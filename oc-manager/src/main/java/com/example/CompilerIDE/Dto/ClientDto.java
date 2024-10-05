package com.example.CompilerIDE.Dto;

import com.example.CompilerIDE.providers.Project;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

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
