package com.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * zip 简历解析相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "zip.resume")
public class ZipResumeProperties {

    /**
     * 单个 zip 中允许的最大文件数
     */
    private int maxFiles;

    /**
     * 单个文件允许的最大字节数
     */
    private int maxSingleFileBytes;

    /**
     * 读取 zip 条目的缓冲区大小
     */
    private int bufferSize;
}

