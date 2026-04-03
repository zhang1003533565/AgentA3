package com.example.appbackend.service;

import com.example.appbackend.entity.CarouselBanner;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface CarouselBannerService {

    CarouselBanner uploadBanner(MultipartFile file, String title, Integer sortOrder);

    /**
     * 获取所有已启用的轮播图（供前端展示）
     */
    List<CarouselBanner> getEnabledBanners();

    /**
     * 获取所有轮播图（供后台管理）
     */
    List<CarouselBanner> getAllBanners();

    /**
     * 根据ID获取轮播图详情
     */
    CarouselBanner getBannerById(Long id);

    /**
     * 更新轮播图信息
     */
    CarouselBanner updateBanner(Long id, String title, Integer sortOrder, Boolean enabled);

    /**
     * 删除轮播图
     */
    void deleteBanner(Long id);
}
