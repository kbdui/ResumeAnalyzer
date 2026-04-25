package com.app.web.repository;

import com.app.entity.TaskDO;
import com.app.service.repository.TaskService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/task")
@CrossOrigin
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 按业务 taskId 查看任务
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDO> getTask(@PathVariable("taskId") String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        return ApiResponse.success(task);
    }

    /**
     * 查看任务列表（按创建时间倒序）
     */
    @GetMapping("/list")
    public ApiResponse<List<TaskDO>> listTask() {
        return ApiResponse.success(taskService.listAll());
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Boolean> deleteTask(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        boolean deleted = taskService.deleteByBusinessTaskId(taskId);
        if (!deleted) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        return ApiResponse.success(true);
    }
}
