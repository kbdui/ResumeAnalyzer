package com.app.web;

import com.app.dto.TextDTO;
import com.app.dto.TaskUploadResponseDTO;
import com.app.service.repository.TaskService;
import com.app.service.ResumeSaveService;
import com.app.tool.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/save")
@CrossOrigin
public class ResumeSaveController {
    private final ResumeSaveService resumeSaveService;
    private final TaskService taskService;

    public ResumeSaveController(ResumeSaveService resumeSaveService,
                                TaskService taskService) {
        this.resumeSaveService = resumeSaveService;
        this.taskService = taskService;
    }

    /**
     * 上传 zip，解析为文本数组并入库 text 表
     */
    @PostMapping("/zip/texts")
    public ApiResponse<TaskUploadResponseDTO> parseZipToTexts(@RequestParam("file") MultipartFile file) {
        try {
            List<TextDTO> texts = resumeSaveService.parseZipToTexts(file);
            TaskUploadResponseDTO response = taskService.createTaskAndSaveResumes(texts);
            return ApiResponse.success(response);
        } catch (IOException | IllegalArgumentException e) {
            return ApiResponse.error(400, "zip解析或入库失败: " + e.getMessage());
        }
    }
}
