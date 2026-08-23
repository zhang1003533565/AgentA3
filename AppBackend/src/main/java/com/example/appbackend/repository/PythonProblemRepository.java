package com.example.appbackend.repository;

import com.example.appbackend.entity.PythonProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PythonProblemRepository
        extends JpaRepository<PythonProblem, Long>, JpaSpecificationExecutor<PythonProblem> {

    /** 上架题目，按题号排序（小程序题库页） */
    List<PythonProblem> findByEnabledTrueOrderByNumberAsc();

    /** 全部题目，按题号排序（管理端） */
    List<PythonProblem> findAllByOrderByNumberAsc();

    Optional<PythonProblem> findByNumber(Integer number);

    /** 当前最大题号（AI 生成题目时自动分配用） */
    @Query("SELECT MAX(p.number) FROM PythonProblem p")
    Integer findMaxNumber();
}
