package com.example.appbackend.repository;

import com.example.appbackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
           "(:username IS NULL OR u.username LIKE %:username%) AND " +
           "(:role IS NULL OR u.role.name = :role) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> findByConditions(@Param("username") String username,
                                @Param("role") String role,
                                @Param("status") Integer status,
                                Pageable pageable);
}
