package com.example.appbackend.controller;

import com.example.appbackend.entity.PaperLayout;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PaperLayoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/papers/{paperId}/layout")
public class PaperLayoutController {
    private final PaperLayoutService service;

    public PaperLayoutController(PaperLayoutService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PaperLayout> get(@PathVariable Long paperId,
                                   @RequestParam(defaultValue = "false") boolean defaults,
                                   @RequestParam(required = false) String templateName,
                                   HttpServletRequest request) {
        Long userId = user(request);
        return Result.success(defaults ? service.getDefaults(paperId, userId, templateName) : service.get(paperId, userId));
    }

    @PutMapping
    public Result<PaperLayout> save(@PathVariable Long paperId, @RequestBody PaperLayout layout,
                                    HttpServletRequest request) {
        return Result.success("版式已保存", service.save(paperId, layout, user(request)));
    }

    private Long user(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (id == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) id;
    }
}
