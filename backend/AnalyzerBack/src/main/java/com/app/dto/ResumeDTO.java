package com.app.dto;

import com.app.dto.resumeDetail.EducationDTO;
import com.app.dto.resumeDetail.PersonalInfoDTO;
import com.app.dto.resumeDetail.ProjectDTO;
import com.app.dto.resumeDetail.WorkExperienceDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 简历信息 DTO
 * 用于接收从大语言模型 API 返回的简历解析结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeDTO {
    
    @JsonProperty("personal_info")
    private PersonalInfoDTO personalInfo;
    
    private List<EducationDTO> education;
    
    @JsonProperty("work_experience")
    private List<WorkExperienceDTO> workExperience;
    
    private List<String> skills;
    
    private List<ProjectDTO> projects;
    
    private List<String> certificates;
}

