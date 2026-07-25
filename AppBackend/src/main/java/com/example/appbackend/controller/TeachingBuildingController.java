package com.example.appbackend.controller;

import com.example.appbackend.dto.ClassroomDTO;
import com.example.appbackend.dto.TeachingBuildingDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.TeachingBuildingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teaching")
public class TeachingBuildingController {
    private final TeachingBuildingService teachingBuildingService;

    public TeachingBuildingController(TeachingBuildingService teachingBuildingService) {
        this.teachingBuildingService = teachingBuildingService;
    }

    @GetMapping("/buildings")
    public Result<List<TeachingBuildingDTO>> listBuildings() {
        return Result.success(teachingBuildingService.listBuildings());
    }

    @GetMapping("/buildings/{facilityId}")
    public Result<TeachingBuildingDTO> getBuilding(@PathVariable Long facilityId) {
        return Result.success(teachingBuildingService.getBuilding(facilityId));
    }

    @PostMapping("/buildings/{facilityId}/classrooms")
    public Result<ClassroomDTO> createClassroom(@PathVariable Long facilityId,
                                                @Valid @RequestBody ClassroomDTO request,
                                                HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return Result.success("教室创建成功", teachingBuildingService.createClassroom(facilityId, request));
    }

    @PutMapping("/classrooms/{classroomId}")
    public Result<ClassroomDTO> updateClassroom(@PathVariable Long classroomId,
                                                @Valid @RequestBody ClassroomDTO request,
                                                HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return Result.success("教室更新成功", teachingBuildingService.updateClassroom(classroomId, request));
    }

    @DeleteMapping("/classrooms/{classroomId}")
    public Result<Void> deleteClassroom(@PathVariable Long classroomId, HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        teachingBuildingService.deleteClassroom(classroomId);
        return Result.success("教室删除成功", null);
    }

    private void checkAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }
}
