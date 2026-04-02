package com.app.service.repository;

import com.app.dao.TextDAO;
import com.app.dto.TextDTO;
import com.app.entity.TextDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextService {

    private final TextDAO textDAO;

    public TextService(TextDAO textDAO) {
        this.textDAO = textDAO;
    }

    /**
     * 批量保存原始简历文本
     */
    @Transactional
    public int saveAll(Long taskId, List<TextDTO> dtos) {
        return saveAllAndReturn(taskId, dtos).size();
    }

    /**
     * 批量保存原始简历文本并返回已入库记录
     */
    @Transactional
    public List<TextDO> saveAllAndReturn(Long taskId, List<TextDTO> dtos) {
        if (taskId == null || dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<TextDO> savedList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (TextDTO dto : dtos) {
            if (dto == null || dto.getText() == null || dto.getText().isBlank()) {
                continue;
            }
            TextDO entity = new TextDO();
            entity.setTaskId(taskId);
            entity.setResumeId(dto.getResumeId());
            entity.setFileName(dto.getFileName());
            entity.setText(dto.getText());
            entity.setCreateTime(now);
            textDAO.insert(entity);
            savedList.add(entity);
        }
        return savedList;
    }
}
