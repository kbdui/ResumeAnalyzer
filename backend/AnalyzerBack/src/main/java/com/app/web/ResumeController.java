package com.app.web;

import com.app.dto.ResumeDTO;
import com.app.dto.TaskResumeMainViewDTO;
import com.app.entity.TaskResumeMainDO;
import com.app.service.ResumeService;
import com.app.service.TaskResumeMainService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/resume")
@CrossOrigin
public class ResumeController {

    private final TaskResumeMainService taskResumeMainService;
    private final ResumeService resumeService;

    public ResumeController(TaskResumeMainService taskResumeMainService,
                            ResumeService resumeService) {
        this.taskResumeMainService = taskResumeMainService;
        this.resumeService = resumeService;
    }

    /**
     * 查询某 task 下关联的简历主表内容
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<List<TaskResumeMainViewDTO>> listByTaskId(@PathVariable("taskId") String taskId) {
        List<TaskResumeMainDO> relations = taskResumeMainService.listRelationsByBusinessTaskId(taskId);
        List<TaskResumeMainViewDTO> response = new ArrayList<>();
        for (TaskResumeMainDO relation : relations) {
            TaskResumeMainViewDTO viewDTO = new TaskResumeMainViewDTO();
            viewDTO.setRelationId(relation.getId());
            viewDTO.setResumeId(relation.getResumeId());
            viewDTO.setRankNo(relation.getRankNo());
            viewDTO.setFinalScore(relation.getFinalScore());
            viewDTO.setCreateTime(relation.getCreateTime());
            viewDTO.setResume(resumeService.getResumeDetailById(relation.getResumeId()));
            response.add(viewDTO);
        }
        return ApiResponse.success(response);
    }

    /**
     * 查询某 task 下、指定 deepseek analyzeTaskId 的深度分析入库结果
     */
    @GetMapping("/task/{taskId}/analyze/{analyzeTaskId}")
    public ApiResponse<List<TaskResumeMainViewDTO>> listByTaskIdAndAnalyzeTaskId(
            @PathVariable("taskId") String taskId,
            @PathVariable("analyzeTaskId") String analyzeTaskId
    ) {
        List<TaskResumeMainDO> relations = taskResumeMainService.listRelationsByBusinessTaskIdAndAnalyzeTaskId(taskId, analyzeTaskId);
        List<TaskResumeMainViewDTO> response = new ArrayList<>();
        for (TaskResumeMainDO relation : relations) {
            TaskResumeMainViewDTO viewDTO = new TaskResumeMainViewDTO();
            viewDTO.setRelationId(relation.getId());
            viewDTO.setResumeId(relation.getResumeId());
            viewDTO.setRankNo(relation.getRankNo());
            viewDTO.setFinalScore(relation.getFinalScore());
            viewDTO.setCreateTime(relation.getCreateTime());
            viewDTO.setResume(resumeService.getResumeDetailById(relation.getResumeId()));
            response.add(viewDTO);
        }
        return ApiResponse.success(response);
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
}
