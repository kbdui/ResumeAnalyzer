package com.app.web;

import com.app.entity.TaskDO;
import com.app.entity.TaskResultDO;
import com.app.service.TaskResultService;
import com.app.service.TaskService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
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
    private final TaskResultService taskResultService;

    public TaskController(TaskService taskService, TaskResultService taskResultService) {
        this.taskService = taskService;
        this.taskResultService = taskResultService;
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

    /**
     * 按业务 taskId 查看其 task_result 列表
     */
    @GetMapping("/{taskId}/results")
    public ApiResponse<List<TaskResultDO>> listTaskResults(@PathVariable("taskId") String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        return ApiResponse.success(taskResultService.listByTaskId(task.getId()));
    }

    /**
     * 查看单条 task_result
     */
    @GetMapping("/result/{id}")
    public ApiResponse<TaskResultDO> getTaskResult(@PathVariable("id") Long id) {
        TaskResultDO result = taskResultService.getById(id);
        if (result == null) {
            return ApiResponse.error(404, "task_result 不存在: " + id);
        }
        return ApiResponse.success(result);
    }
}
