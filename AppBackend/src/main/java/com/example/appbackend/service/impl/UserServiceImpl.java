package com.example.appbackend.service.impl;

import com.example.appbackend.dto.UserResponse;
import com.example.appbackend.dto.LoginRequest;
import com.example.appbackend.dto.RegisterRequest;
import com.example.appbackend.entity.Role;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.RoleRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.UserService;
import com.example.appbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        if (request.getEmail() != null && !request.getEmail().isEmpty()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail() != null && !request.getEmail().isEmpty() ? request.getEmail() : null);
        user.setPhone(request.getPhone() != null && !request.getPhone().isEmpty() ? request.getPhone() : null);

        // 获取用户选择的角色，如果没有选择或无效则默认为STUDENT
        String requestedRole = request.getRole();
        Role role;
        if (requestedRole != null && !requestedRole.isEmpty()) {
            role = roleRepository.findByName(requestedRole.toUpperCase())
                    .orElseGet(() -> {
                        // 如果角色不存在，使用默认STUDENT
                        return roleRepository.findByName("STUDENT")
                                .orElseThrow(() -> new RuntimeException("默认角色不存在，请先初始化角色数据"));
                    });
        } else {
            role = roleRepository.findByName("STUDENT")
                    .orElseThrow(() -> new RuntimeException("默认角色不存在，请先初始化角色数据"));
        }
        user.setRole(role);

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), roleName(user));
        return new UserResponse(token, user.getUsername(), roleName(user), user.getPhone());
    }

    @Override
    public UserResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!request.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), roleName(user));
        return new UserResponse(token, user.getUsername(), roleName(user), user.getPhone());
    }

    private String roleName(User user) {
        return user.getRole() != null ? user.getRole().getName() : "STUDENT";
    }
}
