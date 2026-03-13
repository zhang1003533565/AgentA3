package com.example.appbackend.config;

import com.example.appbackend.entity.Role;
import com.example.appbackend.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        // 初始化默认角色
        initRole("STUDENT", "学生");
        initRole("TEACHER", "教师");
        initRole("ADMIN", "管理员");
    }

    private void initRole(String roleName, String description) {
        if (!roleRepository.findByName(roleName).isPresent()) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
            System.out.println("初始化角色: " + roleName + " (" + description + ")");
        }
    }
}
