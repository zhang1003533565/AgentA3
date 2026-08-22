package com.example.appbackend.config;

import com.example.appbackend.entity.Role;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.RoleRepository;
import com.example.appbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public DemoDataInitializer(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role student = ensureRole("STUDENT");
        ensureRole("TEACHER");
        Role adminRole = ensureRole("ADMIN");

        ensureDemoUser("zzs", "admin123", "A3演示学生", "13800000000",
                "a3-demo@example.invalid", "A3DEMO001", "SCH000004", student);
        // Web 管理端账号（demo 本地重置密码为 123456）
        ensureDemoUser("admin", "123456", "系统管理员", "13800000001",
                "admin@campus.edu.cn", "ADMIN001", "SCH000001", adminRole);
    }

    private void ensureDemoUser(
            String username,
            String password,
            String realName,
            String phone,
            String email,
            String personalNumber,
            String shareCode,
            Role role
    ) {
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            user.setPassword(password);
            user.setRole(role);
            user.setStatus(1);
            userRepository.save(user);
            log.info("Demo user reset: {} / {}", username, password);
        }, () -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(password);
            user.setRealName(realName);
            user.setPhone(phone);
            user.setEmail(email);
            user.setPersonalNumber(personalNumber);
            user.setShareCode(shareCode);
            user.setRole(role);
            user.setStatus(1);
            userRepository.save(user);
            log.info("Demo user ready: {} / {}", username, password);
        });
    }

    private Role ensureRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            return roleRepository.save(role);
        });
    }
}
