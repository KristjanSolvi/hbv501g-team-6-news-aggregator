package is.hi.team6.newsaggregator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@Configuration
@Profile("demo")
public class DemoAdminConfig {

    @Bean
    ApplicationRunner createDemoAdmin(
            UserAccountRepository accounts,
            PasswordEncoder passwords,
            @Value("${demo.admin.email}") String email,
            @Value("${demo.admin.password}") String password) {

        return args -> {
            if (accounts.findByEmail(email).isEmpty()) {
                var admin = new UserAccount(
                        email,
                        passwords.encode(password)
                );

                admin.changeRole(UserAccount.Role.ADMIN);

                accounts.saveAndFlush(admin);
            }
        };
    }
}