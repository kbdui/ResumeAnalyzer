package com.app.dto.resumeDetail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 教育经历 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EducationDTO {
    
    private String school;
    
    private String major;
    
    private String degree;
    
    @JsonProperty("graduation_year")
    private String graduationYear;
}

