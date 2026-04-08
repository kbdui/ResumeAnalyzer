package com.app.web.repository;

import com.app.entity.AnalysisDO;
import com.app.service.DeepseekAnalyzeService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/deepseek")
@CrossOrigin
public class AnalysisController {

    private final DeepseekAnalyzeService deepseekAnalyzeService;

    public AnalysisController(DeepseekAnalyzeService deepseekAnalyzeService) {
        this.deepseekAnalyzeService = deepseekAnalyzeService;
    }

    /**
     * 查询 task 下最终评估列表（analysis 表）
     */
    @GetMapping("/{taskId}/analysis")
    public ApiResponse<List<AnalysisDO>> listAnalysisByTaskId(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        try {
            List<AnalysisDO> rows = deepseekAnalyzeService.listAnalysisByBusinessTaskId(taskId);
            return ApiResponse.success(rows);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}

