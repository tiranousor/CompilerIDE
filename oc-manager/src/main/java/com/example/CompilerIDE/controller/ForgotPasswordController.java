package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.util.ClientNotFoundException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.query.Param;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
@Controller
public class ForgotPasswordController {

    private final JavaMailSender mailSender;

    private final ClientService clientService;

    private final TemplateEngine templateEngine;

    @Autowired
    public ForgotPasswordController(JavaMailSender mailSender, ClientService clientService,
                                    TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.clientService = clientService;
        this.templateEngine = templateEngine;
    }

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String FROM_NAME = "Восстановление пароля в CompilerIDE";
    private static final String EMAIL_SUBJECT = "Ссылка для сброса пароля";

    @GetMapping("/forgot_password")
    public String showForgotPasswordForm() {
        return "forgot_password_form";
    }

    @PostMapping("/forgot_password")
    public String processForgotPassword(@RequestParam("email") String email, Model model,
                                        HttpServletRequest request) {
        String token = RandomStringUtils.randomAlphanumeric(10);
        try {
            clientService.updateResetPasswordToken(token, email);
            Client client = clientService.getClientByEmail(email)
                    .orElseThrow(() -> new ClientNotFoundException("Email не найден: " + email));

            String resetPasswordLink = getSiteURL(request) + "/reset_password?token=" + token;
            sendEmail(email, resetPasswordLink, client.getUsername());
            model.addAttribute("message", "Вам отправлено письмо с ссылкой для восстановления пароля на электронную почту. Проверьте её прямо сейчас.");
        } catch (ClientNotFoundException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (UnsupportedEncodingException | MessagingException e) {
            model.addAttribute("error", "Ошибка при отправке электронной почты");
        }

        return "forgot_password_form";
    }

    public void sendEmail(String recipientEmail, String link, String username) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(recipientEmail);
        helper.setSubject(EMAIL_SUBJECT);

        Context context = new Context();
        context.setVariable("link", link);
        context.setVariable("username", username);
        String htmlContent = templateEngine.process("email/reset_password_email", context);

        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    private String getSiteURL(HttpServletRequest request) {
        String siteURL = request.getRequestURL().toString();
        return siteURL.replace(request.getServletPath(), "");
    }



    @GetMapping("/reset_password")
    public String showResetPasswordForm(@Param(value = "token") String token, Model model) {
        Client client = clientService.getByResetPasswordToken(token);
        model.addAttribute("token", token);

        if (client == null) {
            return "loginAndRegistration";
        }

        return "reset_password_form";
    }

    @PostMapping("/reset_password")
    public String processResetPassword(@RequestParam("token") String token, @RequestParam("password") String password,
                                       Model model) {

        Client client = clientService.getByResetPasswordToken(token);

        if (client == null) {
            return "loginAndRegistration";
        } else {
            clientService.updatePassword(client, password);
            model.addAttribute("message", "Вы успешно сменили свой пароль.");
        }

        return "loginAndRegistration";
    }
}
