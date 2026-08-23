package com.example.appbackend.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WatermarkHistoryRepository extends JpaRepository<WatermarkHistory, Long> {
    // 按 ID 倒序排列，让最新生成的记录显示在最上面
    List<WatermarkHistory> findAllByOrderByIdDesc(); 
}