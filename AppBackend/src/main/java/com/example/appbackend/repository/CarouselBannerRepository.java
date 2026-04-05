package com.example.appbackend.repository;

import com.example.appbackend.entity.CarouselBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CarouselBannerRepository extends JpaRepository<CarouselBanner, Long> {

    /**
     * 查询所有已启用的轮播图，按排序字段升序排列
     */
    List<CarouselBanner> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * 查询所有轮播图，按排序字段升序排列
     */
    List<CarouselBanner> findAllByOrderBySortOrderAsc();
}
