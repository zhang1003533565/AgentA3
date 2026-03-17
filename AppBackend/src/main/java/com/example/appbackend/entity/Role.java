package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "sys_role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '角色名称'")
    private String name;
}
