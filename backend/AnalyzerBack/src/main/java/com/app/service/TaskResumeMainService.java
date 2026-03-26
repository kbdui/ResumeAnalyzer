package com.app.service;

import com.app.dao.TaskResumeMainDAO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeMainDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    public void saveRelation(Long taskDbId,
                               Long resumeId,
                               Integer rankNo,
                               BigDecimal finalScore,
                               String analyzeTaskId) {
        if (taskDbId == null || resumeId == null) {
            return;
        }
        TaskResumeMainDO relation = new TaskResumeMainDO();
        relation.setTaskId(taskDbId);
        relation.setResumeId(resumeId);
        relation.setRankNo(rankNo);
        relation.setFinalScore(finalScore);
        relation.setAnalyzeTaskId(analyzeTaskId);
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

    /**
     * 查询某 task 下所有“深度分析任务ID”（distinct analyze_task_id）
     * 排序规则：按创建时间/主键倒序，保证最新分析任务排在最前面。
     */
    public List<String> listAnalyzeTaskIdsByBusinessTaskId(String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return List.of();
        }

        List<TaskResumeMainDO> relations = taskResumeMainDAO.selectList(new LambdaQueryWrapper<TaskResumeMainDO>()
                .eq(TaskResumeMainDO::getTaskId, task.getId())
                .orderByDesc(TaskResumeMainDO::getCreateTime)
                .orderByDesc(TaskResumeMainDO::getId));

        Set<String> distinct = new LinkedHashSet<>();
        for (TaskResumeMainDO r : relations) {
            if (r.getAnalyzeTaskId() != null && !r.getAnalyzeTaskId().isBlank()) {
                distinct.add(r.getAnalyzeTaskId());
            }
        }
        return List.copyOf(distinct);
    }

    /**
     * 查询 task + analyzeTaskId 下的深度分析入库结果（task_resume_main 过滤）
     */
    public List<TaskResumeMainDO> listRelationsByBusinessTaskIdAndAnalyzeTaskId(String taskId, String analyzeTaskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null || analyzeTaskId == null || analyzeTaskId.isBlank()) {
            return List.of();
        }

        return taskResumeMainDAO.selectList(new LambdaQueryWrapper<TaskResumeMainDO>()
                .eq(TaskResumeMainDO::getTaskId, task.getId())
                .eq(TaskResumeMainDO::getAnalyzeTaskId, analyzeTaskId)
                .orderByAsc(TaskResumeMainDO::getId));
    }
}
