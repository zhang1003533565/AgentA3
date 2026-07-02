package com.example.appbackend.repository;

import com.example.appbackend.entity.SchedulePeriodSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchedulePeriodSettingRepository extends JpaRepository<SchedulePeriodSetting, Long> {

    List<SchedulePeriodSetting> findByUserIdOrderByPeriodIndexAsc(Long userId);

    Optional<SchedulePeriodSetting> findByUserIdAndPeriodIndex(Long userId, Integer periodIndex);
}
