package com.app.service;

import com.app.dao.ResumeTextDAO;
import com.app.dto.ResumeTextDTO;
import com.app.entity.ResumeTextDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeTextService {

    private final ResumeTextDAO resumeTextDAO;

    public ResumeTextService(ResumeTextDAO resumeTextDAO) {
        this.resumeTextDAO = resumeTextDAO;
    }

    /**
     * 批量保存原始简历文本
     */
    @Transactional
    public int saveAll(List<ResumeTextDTO> dtos) {
        return saveAllAndReturn(dtos).size();
    }

    /**
     * 批量保存原始简历文本并返回已入库记录
     */
    @Transactional
    public List<ResumeTextDO> saveAllAndReturn(List<ResumeTextDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<ResumeTextDO> savedList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ResumeTextDTO dto : dtos) {
            if (dto == null || dto.getText() == null || dto.getText().isBlank()) {
                continue;
            }
            ResumeTextDO entity = new ResumeTextDO();
            entity.setResumeId(dto.getResumeId());
            entity.setFileName(dto.getFileName());
            entity.setText(dto.getText());
            entity.setCreateTime(now);
            resumeTextDAO.insert(entity);
            savedList.add(entity);
        }
        return savedList;
    }
}

