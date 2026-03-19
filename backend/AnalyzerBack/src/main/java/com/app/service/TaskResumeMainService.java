package com.app.service;

import com.app.dao.TaskResumeMainDAO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeMainDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskResumeMainService {

    private final TaskResumeMainDAO taskResumeMainDAO;
    private final TaskService taskService;

    public TaskResumeMainService(TaskResumeMainDAO taskResumeMainDAO,
                                 TaskService taskService) {
        this.taskResumeMainDAO = taskResumeMainDAO;
        this.taskService = taskService;
    }

    @Transactional
    public void saveRelation(Long taskDbId, Long resumeId, Integer rankNo, BigDecimal finalScore) {
        if (taskDbId == null || resumeId == null) {
            return;
        }
        TaskResumeMainDO relation = new TaskResumeMainDO();
        relation.setTaskId(taskDbId);
        relation.setResumeId(resumeId);
        relation.setRankNo(rankNo);
        relation.setFinalScore(finalScore);
        relation.setCreateTime(LocalDateTime.now());
        taskResumeMainDAO.insert(relation);
    }

    public List<TaskResumeMainDO> listRelationsByBusinessTaskId(String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return List.of();
        }
        return taskResumeMainDAO.selectList(new LambdaQueryWrapper<TaskResumeMainDO>()
                .eq(TaskResumeMainDO::getTaskId, task.getId())
                .orderByAsc(TaskResumeMainDO::getId));
    }
}
