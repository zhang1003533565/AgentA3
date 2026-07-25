package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.User;
import jakarta.validation.Valid;

public interface UserService {

    UserResponse register(@Valid RegisterRequest request);



    UserResponse applogin(@Valid LoginRequest request);

    UserResponse weblogin(@Valid LoginRequest request);

    UserResponse current(Long id);

    void password(String username, @Valid PasswordChangeRequest request);

    PageResponse<UserListItem> getUserList(Integer page, Integer size, String username, String role, Integer status);

    void updateUser(Long id, UserUpdateRequest request);

    void enableUser(Long id);

    void disableUser(Long id);

    void resetPassword(Long id, String newPassword);

    User findByUsername(String username);

    void updateAvatar(String username, String avatarUrl);

}
