package com.app.web;

import com.app.dto.ResumeTextDTO;
import com.app.dto.TaskUploadResponseDTO;
import com.app.service.TaskService;
import com.app.service.ZipResumeService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/save")
@CrossOrigin
public class ResumeSaveController {
    private final ZipResumeService zipResumeService;
    private final TaskService taskService;

    public ResumeSaveController(ZipResumeService zipResumeService,
                                TaskService taskService) {
        this.zipResumeService = zipResumeService;
        this.taskService = taskService;
    }

    /**
     * 上传 zip，解析为文本数组并入库 resume_text 表
     */
    @PostMapping("/zip/texts")
    public ApiResponse<TaskUploadResponseDTO> parseZipToTexts(@RequestParam("file") MultipartFile file) {
        try {
            List<ResumeTextDTO> texts = zipResumeService.parseZipToTexts(file);
            TaskUploadResponseDTO response = taskService.createTaskAndSaveResumes(texts);
            return ApiResponse.success(response);
        } catch (IOException | IllegalArgumentException e) {
            return ApiResponse.error(400, "zip解析或入库失败: " + e.getMessage());
        }
    }
}
