package com.app.web;

import com.app.dto.ResumeDTO;
import com.app.service.repository.ResumeService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resume")
@CrossOrigin
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
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
