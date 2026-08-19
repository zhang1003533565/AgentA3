package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.RegistrationListItem;
import com.example.appbackend.entity.Registration;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@Tag(name = "Registration", description = "Registration APIs")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "Forbidden");
        }
    }

    private User getCurrentUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "User not found"));
    }

    @Operation(summary = "Register activity", description = "Student registers for an activity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "400", description = "Registration closed or full")
    })
    @PostMapping
    public Result<Registration> registerActivity(
            @Parameter(description = "Activity ID", required = true, example = "1")
            @RequestParam Long activityId,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        Registration registration = registrationService.registerActivity(activityId, user.getId());
        return Result.success(registration);
    }

    @Operation(summary = "Admin add registration", description = "Admin/teacher manually registers a student for an activity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "400", description = "Duplicate or full")
    })
    @PostMapping("/admin/add")
    public Result<Registration> adminAddRegistration(
            @Parameter(description = "Activity ID", required = true, example = "1")
            @RequestParam Long activityId,
            @Parameter(description = "User ID", required = true, example = "4")
            @RequestParam Long userId,
            HttpServletRequest request) {
        checkRole(request);
        Registration registration = registrationService.adminRegisterActivity(activityId, userId);
        return Result.success(registration);
    }

    @Operation(summary = "Cancel registration", description = "Cancel activity registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Registration not found")
    })
    @DeleteMapping("/{id}")
    public Result<Registration> cancelRegistration(
            @Parameter(description = "Registration ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        Registration registration = registrationService.cancelRegistration(id, user.getId());
        return Result.success(registration);
    }

    @Operation(summary = "All registrations", description = "Admin or teacher gets registrations across activities, filterable by activity/status/keyword")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public Result<PageResponse<RegistrationListItem>> getAllRegistrations(
            @Parameter(description = "Activity ID (optional)", example = "1")
            @RequestParam(required = false) Long activityId,
            @Parameter(description = "Registration status: PENDING/APPROVED/REJECTED (optional)", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "Keyword: realName/username/personalNumber (optional)", example = "zhang")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Page number, starts at 1", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        checkRole(request);
        PageResponse<RegistrationListItem> list = registrationService.getAllRegistrations(activityId, status, keyword, page, size);
        return Result.success(list);
    }

    @Operation(summary = "Manager remove registration", description = "Admin or teacher removes a registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Registration not found")
    })
    @DeleteMapping("/{id}/manage")
    public Result<Void> removeRegistrationByManager(
            @Parameter(description = "Registration ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        checkRole(request);
        registrationService.removeRegistrationByManager(id);
        return Result.success();
    }

    @Operation(summary = "My registrations", description = "Get current user's registrations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/my-registrations")
    public Result<PageResponse<Registration>> getMyRegistrations(
            @Parameter(description = "Page number, starts at 1", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        PageResponse<Registration> list = registrationService.getMyRegistrations(user.getId(), page, size);
        return Result.success(list);
    }

    @Operation(summary = "Activity registrations", description = "Get registrations of an activity (admin/teacher)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Activity not found")
    })
    @GetMapping("/activities/{activityId}/registrations")
    public Result<PageResponse<RegistrationListItem>> getRegistrations(
            @Parameter(description = "Activity ID", required = true, example = "1")
            @PathVariable Long activityId,
            @Parameter(description = "Page number, starts at 1", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        checkRole(request);
        PageResponse<RegistrationListItem> list = registrationService.getActivityRegistrations(activityId, page, size);
        return Result.success(list);
    }

    @Operation(summary = "Audit registration", description = "Approve or reject registration (admin/teacher)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Registration not found")
    })
    @PutMapping("/{id}/audit")
    public Result<Void> auditRegistration(
            @Parameter(description = "Registration ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "Audit status: APPROVED or REJECTED", required = true, example = "APPROVED")
            @RequestParam String auditStatus,
            @Parameter(description = "Remark")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        registrationService.auditRegistration(id, auditStatus, user.getId(), remark);
        return Result.success();
    }

    @Operation(summary = "Batch audit registration", description = "Batch approve/reject registrations (admin/teacher)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PutMapping("/batch-audit")
    public Result<Void> batchAuditRegistration(
            @Parameter(description = "Registration ID list", required = true)
            @RequestParam Long[] registrationIds,
            @Parameter(description = "Audit status: APPROVED or REJECTED", required = true, example = "APPROVED")
            @RequestParam String auditStatus,
            @Parameter(description = "Remark")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        registrationService.batchAuditRegistration(registrationIds, auditStatus, user.getId(), remark);
        return Result.success();
    }
}
