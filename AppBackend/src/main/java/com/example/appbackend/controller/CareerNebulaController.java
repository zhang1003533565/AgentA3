package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.service.CareerNebulaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/career-nebula")
public class CareerNebulaController {

    private final CareerNebulaService service;

    public CareerNebulaController(CareerNebulaService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> getMap() {
        return Result.success(service.getMap());
    }

    @PutMapping
    public Result<Map<String, Object>> saveMap(@RequestBody Map<String, Object> payload) {
        return Result.success("岗位星图保存成功", service.saveMap(payload));
    }
}
