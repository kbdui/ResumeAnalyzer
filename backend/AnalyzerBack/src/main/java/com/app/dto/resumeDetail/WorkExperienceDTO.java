package com.app.dto.resumeDetail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工作经历 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkExperienceDTO {
    
    private String company;
    
    private String position;
    
    private String duration;
    
    private String description;
}

