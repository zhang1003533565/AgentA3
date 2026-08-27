package com.example.appbackend.repository;

import com.example.appbackend.entity.StudyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

    List<StudyTask> findByGoalIdOrderByOrderNumAscIdAsc(Long goalId);

    List<StudyTask> findByGoalIdInOrderByGoalIdAscOrderNumAscIdAsc(Collection<Long> goalIds);

    List<StudyTask> findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(Long goalId);

    List<StudyTask> findByGoalIdAndIsCompletedTrueOrderByOrderNumAscIdAsc(Long goalId);

    long countByGoalId(Long goalId);

    long countByGoalIdAndIsCompletedTrue(Long goalId);

    /** 按目标批量统计任务总数与已完成数，避免列表页 N+1 查询。 */
    @Query("select t.goalId as goalId, count(t) as total, " +
            "sum(case when t.isCompleted = true then 1 else 0 end) as completed " +
            "from StudyTask t where t.goalId in :goalIds group by t.goalId")
    List<TaskCountView> countByGoalIds(@Param("goalIds") Collection<Long> goalIds);

    interface TaskCountView {
        Long getGoalId();

        long getTotal();

        long getCompleted();
    }
}
