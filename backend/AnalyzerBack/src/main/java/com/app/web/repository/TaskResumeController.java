package com.app.web.repository;

import com.app.dao.TaskResumeDAO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.app.service.repository.TaskService;
import com.app.tool.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/task-resume")
@CrossOrigin
public class TaskResumeController {

    private final TaskService taskService;
    private final TaskResumeDAO taskResumeDAO;

    public TaskResumeController(TaskService taskService, TaskResumeDAO taskResumeDAO) {
        this.taskService = taskService;
        this.taskResumeDAO = taskResumeDAO;
    }

    /**
     * 查询 task 下硬过滤结果（task_resume，包括 analysis_json）。
     */
    @GetMapping("/{taskId}/list")
    public ApiResponse<List<TaskResumeDO>> listByTaskId(@PathVariable("taskId") String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        List<TaskResumeDO> rows = taskResumeDAO.selectList(new LambdaQueryWrapper<TaskResumeDO>()
                .eq(TaskResumeDO::getTaskId, task.getId())
                .orderByDesc(TaskResumeDO::getUpdateTime)
                .orderByDesc(TaskResumeDO::getId));
        return ApiResponse.success(rows);
    }
}

