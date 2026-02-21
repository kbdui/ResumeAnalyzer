package com.app.dto.resumeDetail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 项目经历 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectDTO {
    
    private String name;
    
    private String description;
    
    private List<String> technologies;
}

