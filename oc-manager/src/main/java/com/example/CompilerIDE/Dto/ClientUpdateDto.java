package com.example.CompilerIDE.Dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientUpdateDto {
    private Integer id;
    @NotBlank(message = "Username is mandatory")
    private String username;

    @Email(message = "Please enter a valid email address")
    @NotBlank(message = "Email is mandatory")
    private String email;

    // Пароль не требуется при обновлении профиля
    private String password;
    private String about;
    private String githubProfile;
    private String avatarUrl;

}
