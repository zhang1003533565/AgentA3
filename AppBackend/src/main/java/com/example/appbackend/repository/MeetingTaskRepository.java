package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingTask;
import com.example.appbackend.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会议任务数据访问层
 */
@Repository
public interface MeetingTaskRepository extends JpaRepository<MeetingTask, Long> {

    /**
     * 根据会议 Session ID 查询所有任务
     */
    List<MeetingTask> findByMeetingSessionIdOrderByCreateTimeDesc(Long meetingSessionId);

    /**
     * 根据任务负责人用户 ID 查询个人任务（权限控制：只能查自己的）
     */
    @Query("SELECT t FROM MeetingTask t WHERE t.assigneeId = :userId ORDER BY t.createTime DESC")
    List<MeetingTask> findByAssigneeId(@Param("userId") Long userId);

    /**
     * 根据任务负责人用户 ID 和状态查询个人任务
     */
    @Query("SELECT t FROM MeetingTask t WHERE t.assigneeId = :userId AND t.status = :status ORDER BY t.createTime DESC")
    List<MeetingTask> findByAssigneeIdAndStatus(@Param("userId") Long userId, @Param("status") TaskStatus status);

    /**
     * 根据任务负责人用户 ID 和会议 ID 查询任务
     */
    @Query("SELECT t FROM MeetingTask t WHERE t.assigneeId = :userId AND t.meetingSessionId = :meetingSessionId ORDER BY t.createTime DESC")
    List<MeetingTask> findByAssigneeIdAndMeetingSessionId(@Param("userId") Long userId, @Param("meetingSessionId") Long meetingSessionId);

    /**
     * 检查是否存在重复任务（同一会议、同一负责人、相同标题）
     */
    boolean existsByMeetingSessionIdAndAssigneeIdAndTitle(Long meetingSessionId, Long assigneeId, String title);

    /**
     * 根据会议 ID 和负责人查询指定标题的任务（用于幂等判断）
     */
    @Query("SELECT t FROM MeetingTask t WHERE t.meetingSessionId = :meetingSessionId AND t.assigneeId = :assigneeId AND t.title = :title")
    List<MeetingTask> findByMeetingSessionIdAndAssigneeIdAndTitle(
            @Param("meetingSessionId") Long meetingSessionId,
            @Param("assigneeId") Long assigneeId,
            @Param("title") String title
    );
}
