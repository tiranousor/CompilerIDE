package com.example.CompilerIDE.controller;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;

@Controller
public class UnbanRequestController {

    private final JavaMailSender mailSender;

    @Autowired
    public UnbanRequestController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username}")
    private String projectEmail;

    private static final String SUBJECT = "Запрос на разблокировку аккаунта";

    @GetMapping("/unbanRequest")
    public String showUnbanRequestPage(Model model, @RequestParam("email") String email) {
        model.addAttribute("email", email);
        return "banned";
    }


    @PostMapping("/sendUnbanRequest")
    public String sendUnbanRequest(@RequestParam("email") String email, @RequestParam("message") String message, Model model) {
        try {
            sendEmail(projectEmail, email, message);
            model.addAttribute("message", "Ваш запрос на разблокировку был отправлен.");
        } catch (MessagingException | UnsupportedEncodingException e) {
            model.addAttribute("error", "Ошибка при отправке запроса.");
        }
        return "banned";
    }

    private void sendEmail(String toEmail, String fromEmail, String messageContent) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, "Заблокированный пользователь");
        helper.setTo(toEmail);
        helper.setSubject(SUBJECT);

        String content = "<p>Запрос от пользователя: " + fromEmail + "</p>";
        content += "<p>Сообщение:</p>";
        content += "<p>" + messageContent + "</p>";

        helper.setText(content, true);

        mailSender.send(message);
    }
}
