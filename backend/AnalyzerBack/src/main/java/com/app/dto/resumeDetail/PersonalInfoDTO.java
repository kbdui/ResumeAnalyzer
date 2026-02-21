package com.app.dto.resumeDetail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 个人信息 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonalInfoDTO {
    
    private String name;
    
    private String contact;
    
    private String email;
}

