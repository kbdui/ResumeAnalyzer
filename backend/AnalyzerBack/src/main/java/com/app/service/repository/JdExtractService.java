package com.app.service.repository;

import com.app.dao.JdExtractDAO;
import com.app.entity.JdExtractDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class JdExtractService {

    private final JdExtractDAO jdExtractDAO;

    public JdExtractService(JdExtractDAO jdExtractDAO) {
        this.jdExtractDAO = jdExtractDAO;
    }

    public JdExtractDO getByTaskId(Long taskDbId) {
        if (taskDbId == null) {
            return null;
        }
        return jdExtractDAO.selectOne(new LambdaQueryWrapper<JdExtractDO>()
                .eq(JdExtractDO::getTaskId, taskDbId)
                .last("LIMIT 1"));
    }

    @Transactional
    public JdExtractDO upsertByTaskId(Long taskDbId,
                                      String jdText,
                                      String workExperienceKeywords,
                                      String skillsKeywords,
                                      String educationKeywords) {
        if (taskDbId == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        JdExtractDO existing = getByTaskId(taskDbId);
        if (existing == null) {
            JdExtractDO created = new JdExtractDO();
            created.setTaskId(taskDbId);
            created.setJdText(jdText);
            created.setWorkExperienceKeywords(workExperienceKeywords);
            created.setSkillsKeywords(skillsKeywords);
            created.setEducationKeywords(educationKeywords);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            jdExtractDAO.insert(created);
            return created;
        }
        JdExtractDO update = new JdExtractDO();
        update.setId(existing.getId());
        update.setJdText(jdText);
        update.setWorkExperienceKeywords(workExperienceKeywords);
        update.setSkillsKeywords(skillsKeywords);
        update.setEducationKeywords(educationKeywords);
        update.setUpdateTime(now);
        jdExtractDAO.updateById(update);
        return jdExtractDAO.selectById(existing.getId());
    }
}

