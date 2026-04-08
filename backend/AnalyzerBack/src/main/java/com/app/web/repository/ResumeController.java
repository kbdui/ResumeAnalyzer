package com.app.web.repository;

import com.app.dao.TextDAO;
import com.app.dto.ResumeDTO;
import com.app.entity.TaskDO;
import com.app.entity.TextDO;
import com.app.service.repository.ResumeService;
import com.app.service.repository.TaskService;
import com.app.tool.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/resume")
@CrossOrigin
public class ResumeController {
    private final ResumeService resumeService;
    private final TaskService taskService;
    private final TextDAO textDAO;

    public ResumeController(ResumeService resumeService,
                            TaskService taskService,
                            TextDAO textDAO) {
        this.resumeService = resumeService;
        this.taskService = taskService;
        this.textDAO = textDAO;
    }

    /**
     * 查询单个简历主表内容
     */
    @GetMapping("/{resumeId}")
    public ApiResponse<ResumeDTO> getByResumeId(@PathVariable("resumeId") Long resumeId) {
        ResumeDTO resume = resumeService.getResumeDetailById(resumeId);
        if (resume == null) {
            return ApiResponse.error(404, "resume 不存在: " + resumeId);
        }
        return ApiResponse.success(resume);
    }

    /**
     * 查询 task 下已提取的结构化简历信息（resume 及其关联表）。
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<List<ResumeDTO>> listByTaskId(@PathVariable("taskId") String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "task 不存在: " + taskId);
        }
        List<TextDO> textRows = textDAO.selectList(new LambdaQueryWrapper<TextDO>()
                .eq(TextDO::getTaskId, task.getId())
                .orderByAsc(TextDO::getId));
        if (textRows == null || textRows.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        LinkedHashSet<String> businessResumeIds = new LinkedHashSet<>();
        for (TextDO row : textRows) {
            if (row == null || row.getResumeId() == null || row.getResumeId().isBlank()) {
                continue;
            }
            businessResumeIds.add(row.getResumeId().trim());
        }

        List<ResumeDTO> result = new ArrayList<>();
        for (String businessResumeId : businessResumeIds) {
            ResumeDTO dto = resumeService.getResumeDetailByBusinessResumeId(businessResumeId);
            if (dto != null) {
                result.add(dto);
            }
        }
        return ApiResponse.success(result);
    }
}

