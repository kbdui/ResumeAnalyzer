package com.app.dto;

import com.app.dto.resumeDetail.EducationDTO;
import com.app.dto.resumeDetail.ProjectDTO;
import com.app.dto.resumeDetail.WorkExperienceDTO;
import lombok.Data;

import java.util.List;

/**
 * 文本传输对象（用于批量上传与 Python 服务传输）
 */
@Data
public class TextDTO {
    private String resumeId;
    private String fileName;
    private String text;
    private String rawText;
    private String summary;
    private List<String> skills;
    private List<String> keywords;
    private List<WorkExperienceDTO> workExperience;
    private List<ProjectDTO> projects;
    private List<EducationDTO> education;
    private List<String> industryTags;
    private List<String> roleTags;
    private Double yearsOfExperience;
    private String managementLevel;
    private String hardFilterResult;
}
