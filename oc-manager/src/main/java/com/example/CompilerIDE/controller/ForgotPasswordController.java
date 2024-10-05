package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ClientService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.UnsupportedEncodingException;
@Controller
public class ForgotPasswordController {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ClientService clientService;

    @GetMapping("/forgot_password")
    public String showForgotPasswordForm() {
        return "forgot_password_form";
    }

    @PostMapping("/forgot_password")
    public String processForgotPassword(HttpServletRequest request, Model model) {
        String email = request.getParameter("email");
        String token = RandomStringUtils.randomAlphanumeric(10);
        try {
            clientService.updateResetPasswordToken(token, email);
            String resetPasswordLink = Utility.getSiteURL(request) + "/reset_password?token=" + token;
            sendEmail(email, resetPasswordLink);
            model.addAttribute("message", "Вам отправлено письмо с ссылкой для восстановления пароля на электронную почту. Проверьте её прямо сейчас.");

//        } catch (ClientNotFoundException ex) {
//            model.addAttribute("error", ex.getMessage());
        } catch (UnsupportedEncodingException | MessagingException e) {
            model.addAttribute("error", "Error while sending email");
        }

        return "forgot_password_form";
    }
    public void sendEmail(String recipientEmail, String link)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        helper.setFrom("serovakatya2911@gmail.com", "Восстановление пароля в CompilerIDE");
        helper.setTo(recipientEmail);

        String subject = "Here's the link to reset your password";

        String content = "<p>Здравствуйте,</p>"
                + "<p>Вы попросили восстановить ваш пароль.</p>"
                + "<p>Кликните по ссылке ниже, чтобы изменить пароль:</p>"
                + "<p><a href=\"" + link + "\">Изменить пароль</a></p>"
                + "<br>"
                + "<p>Не игнорируйте это письмо, если вы помните свой пароль"
                + " или не отправляли запрос на восстановление.</p>";

        helper.setSubject(subject);

        helper.setText(content, true);

        mailSender.send(message);
    }


    @GetMapping("/reset_password")
    public String showResetPasswordForm(@Param(value = "token") String token, Model model) {
        Client client = clientService.getByResetPasswordToken(token);
        model.addAttribute("token", token);

        if (client == null) {
            model.addAttribute("message", "Invalid Token");
            return "message";
        }

        return "reset_password_form";
    }

    @PostMapping("/reset_password")
    public String processResetPassword(HttpServletRequest request, Model model) {
        String token = request.getParameter("token");
        String password = request.getParameter("password");

        Client client = clientService.getByResetPasswordToken(token);
        model.addAttribute("title", "Reset your password");

        if (client == null) {
            model.addAttribute("message", "Invalid Token");
            return "message";
        } else {
            clientService.updatePassword(client, password);

            model.addAttribute("message", "You have successfully changed your password.");
        }

        return "loginAndRegistration";
    }
}
