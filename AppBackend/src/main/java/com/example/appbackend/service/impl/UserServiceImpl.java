package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Role;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.RoleRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.UserService;
import com.example.appbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

        // 设置可选字段
        if (request.getRealName() != null && !request.getRealName().isEmpty()) {
            user.setRealName(request.getRealName());
        }
        if (request.getCollege() != null && !request.getCollege().isEmpty()) {
            user.setCollege(request.getCollege());
        }
        if (request.getMajor() != null && !request.getMajor().isEmpty()) {
            user.setMajor(request.getMajor());
        }
        if (request.getClassName() != null && !request.getClassName().isEmpty()) {
            user.setClassName(request.getClassName());
        }
        if (request.getPersonalNumber() != null && !request.getPersonalNumber().isEmpty()) {
            user.setPersonalNumber(request.getPersonalNumber());
        }

        // 获取用户选择的角色，如果没有选择或无效则默认为STUDENT
        String requestedRole = request.getRole();
        Role role;
        if (requestedRole != null && !requestedRole.isEmpty()) {
            role = roleRepository.findByName(requestedRole.toUpperCase())
                    .orElseThrow(() -> new RuntimeException("请选择有效身份"));
        } else {
            role = roleRepository.findByName("STUDENT")
                    .orElseThrow(() -> new RuntimeException("无角色存在"));
        }
        user.setRole(role);

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), roleName(user));
        return new UserResponse(token, user.getUsername(), roleName(user), user.getPhone(),
                user.getRealName(), user.getCollege(), user.getMajor(), user.getClassName(), user.getPersonalNumber());
    }

    @Override
    public UserResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!request.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), roleName(user));
        return new UserResponse(token, user.getUsername(), roleName(user), user.getPhone(),
                user.getRealName(), user.getCollege(), user.getMajor(), user.getClassName(), user.getPersonalNumber());
    }

    @Override
    public UserResponse applogin(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        // 安全且逻辑正确的写法
        if(!"STUDENT".equals(roleName(user))){
            throw new RuntimeException("请登录学生用户");
        }

        if (!request.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), roleName(user));
        return new UserResponse(token, user.getUsername(), roleName(user), user.getPhone(),
                user.getRealName(), user.getCollege(), user.getMajor(), user.getClassName(), user.getPersonalNumber());
    }

    @Override
    public UserResponse current(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        // 当前用户信息接口不返回 token，由前端已有 token 保持登录状态
        return new UserResponse(null, user.getUsername(), roleName(user), user.getPhone(),
                user.getRealName(), user.getCollege(), user.getMajor(), user.getClassName(), user.getPersonalNumber());
    }

    @Override
    public void password(String username, PasswordChangeRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));


        if (!user.getPassword().equals(request.getOldPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        if(user.getPassword().equals(request.getNewPassword())){
            throw new RuntimeException("新密码不可与旧密码相同");
        }

        user.setPassword(request.getNewPassword());
        userRepository.save(user);
    }

    @Override
    public PageResponse<UserListItem> getUserList(Integer page, Integer size, String username, String role, Integer status) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<User> userPage = userRepository.findByConditions(username, role, status, pageable);

        List<UserListItem> records = new ArrayList<>();
        for (User user : userPage.getContent()) {
            UserListItem item = new UserListItem();
            item.setId(user.getId());
            item.setUsername(user.getUsername());
            item.setRealName(user.getRealName());
            item.setPersonalNumber(user.getPersonalNumber());
            item.setPhone(user.getPhone());
            item.setEmail(user.getEmail());
            item.setRole(roleName(user));
            item.setCollege(user.getCollege());
            item.setMajor(user.getMajor());
            item.setClassName(user.getClassName());
            item.setStatus(user.getStatus());
            item.setCreateTime(user.getCreateTime());
            records.add(item);
        }

        return new PageResponse<>(records, userPage.getTotalElements(), page, size);
    }

    @Override
    public void updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getCollege() != null) {
            user.setCollege(request.getCollege());
        }
        if (request.getMajor() != null) {
            user.setMajor(request.getMajor());
        }
        if (request.getClassName() != null) {
            user.setClassName(request.getClassName());
        }

        userRepository.save(user);
    }

    @Override
    public void enableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setStatus(1);
        userRepository.save(user);
    }

    @Override
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setStatus(0);
        userRepository.save(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(newPassword);
        userRepository.save(user);
    }


    private String roleName(User user) {
        return user.getRole() != null ? user.getRole().getName() : "STUDENT";
    }
}
