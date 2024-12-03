    package com.example.CompilerIDE.config;
    import com.example.CompilerIDE.providers.Client;
    import com.example.CompilerIDE.services.ClientService;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.CommandLineRunner;
    import org.springframework.stereotype.Component;
    import org.springframework.transaction.annotation.Transactional;


    @Component
    public class AdminInitializer implements CommandLineRunner {

        private final ClientService clientService;

        @Autowired
        public AdminInitializer(ClientService clientService) {
            this.clientService = clientService;
        }
        @Transactional
        @Override
        public void run(String... args) throws Exception {
            if (!clientService.existsByUsername("admin")) {
                Client admin = new Client();
                admin.setUsername("admin");
                admin.setPassword("admin");
                admin.setEmail("admin@mail.ru");
                admin.setRole("ROLE_ADMIN");
                clientService.save(admin);
                System.out.println("Системный администратор создан.");
            } else {
                System.out.println("Системный администратор уже существует.");
            }
        }
    }
