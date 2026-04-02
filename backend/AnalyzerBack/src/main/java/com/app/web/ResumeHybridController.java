package com.app.web;

import com.app.config.PythonTaskSetProperties;
import com.app.entity.HybridResultDO;
import com.app.entity.TaskDO;
import com.app.request.ResumeHybridRequest;
import com.app.service.repository.HybridResultService;
import com.app.service.ResumeHybridService;
import com.app.service.repository.TaskService;
import com.app.tool.ApiResponse;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/hybrid")
@CrossOrigin
public class ResumeHybridController {
    private final ResumeHybridService resumeHybridService;

    private final PythonTaskSetProperties properties;

    private final TaskService taskService;

    private final HybridResultService hybridResultService;

    public ResumeHybridController(ResumeHybridService resumeHybridService,
                                  PythonTaskSetProperties properties,
                                  TaskService taskService,
                                  HybridResultService hybridResultService){
        this.resumeHybridService = resumeHybridService;
        this.properties = properties;
        this.taskService = taskService;
        this.hybridResultService = hybridResultService;
    }

    /**
     * 提交匹配异步任务到 Python FastAPI（多份简历文本）
     */
    @PostMapping("/match/task")
    public ApiResponse<String> submitMatchTask(@RequestBody ResumeHybridRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId().isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (request.getJdText() == null || request.getJdText().isBlank()) {
            return ApiResponse.error(400, "jdText 不能为空");
        }
        try {
            Integer topK = request.getTopK() == null ? properties.getTopK() : request.getTopK();
            Integer recallK = request.getRecallK() == null ? properties.getRecallK() : request.getRecallK();
            String pythonTaskId = resumeHybridService.submitByTaskId(
                    request.getTaskId(),
                    request.getJdText(),
                    topK,
                    recallK
            );
            return ApiResponse.success(pythonTaskId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IOException e) {
            return ApiResponse.error(500, "提交任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询 Python 匹配任务状态
     */
    @GetMapping("/match/task/{taskId}")
    public ApiResponse<JsonNode> getMatchTask(@PathVariable("taskId") String taskId) {
        try {
            JsonNode result = resumeHybridService.queryAndStoreResult(taskId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IOException e) {
            return ApiResponse.error(500, "查询任务失败: " + e.getMessage());
        }
    }

    /**
     * 按业务 taskId 查看其 hybrid_result 列表
     */
    @GetMapping("/{taskId}/results")
    public ApiResponse<List<HybridResultDO>> listTaskResults(@PathVariable("taskId") String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        return ApiResponse.success(hybridResultService.listByTaskId(task.getId()));
    }
}
