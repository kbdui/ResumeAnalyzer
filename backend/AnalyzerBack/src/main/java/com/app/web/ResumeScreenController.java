package com.app.web;

import com.app.config.PythonTaskSetProperties;
import com.app.request.TaskSubmitMatchRequest;
import com.app.service.TaskMatchService;
import com.app.tool.ApiResponse;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/screen")
@CrossOrigin
public class ResumeScreenController {
    private final TaskMatchService taskMatchService;

    private final PythonTaskSetProperties properties;

    // 构造器上方可以省略不写@Autowired
    public ResumeScreenController(TaskMatchService taskMatchService, PythonTaskSetProperties properties){
        this.taskMatchService = taskMatchService;
        this.properties = properties;
    }

    /**
     * 提交匹配异步任务到 Python FastAPI（多份简历文本）
     */
    @PostMapping("/match/task")
    public ApiResponse<String> submitMatchTask(@RequestBody TaskSubmitMatchRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId().isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (request.getJdText() == null || request.getJdText().isBlank()) {
            return ApiResponse.error(400, "jdText 不能为空");
        }
        try {
            Integer topK = request.getTopK() == null ? properties.getTopK() : request.getTopK();
            Integer recallK = request.getRecallK() == null ? properties.getRecallK() : request.getRecallK();
            String pythonTaskId = taskMatchService.submitByTaskId(
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
            JsonNode result = taskMatchService.queryAndStoreResult(taskId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IOException e) {
            return ApiResponse.error(500, "查询任务失败: " + e.getMessage());
        }
    }
}
