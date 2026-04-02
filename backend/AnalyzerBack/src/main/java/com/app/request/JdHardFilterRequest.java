package com.app.request;

import lombok.Data;

/**
 * 按 JD 对 task 下简历做三态硬过滤
 */
@Data
public class JdHardFilterRequest {

    /**
     * 岗位描述文本
     */
    private String jdText;
}
