package com.app.service;

import com.app.dao.TaskResumeDAO;
import com.app.dao.TaskDAO;
import com.app.dto.ResumeTextDTO;
import com.app.dto.TaskUploadResponseDTO;
import com.app.entity.ResumeTextDO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskDAO taskDAO;
    private final TaskResumeDAO taskResumeDAO;
    private final ResumeTextService resumeTextService;

    public TaskService(TaskDAO taskDAO,
                       TaskResumeDAO taskResumeDAO,
                       ResumeTextService resumeTextService) {
        this.taskDAO = taskDAO;
        this.taskResumeDAO = taskResumeDAO;
        this.resumeTextService = resumeTextService;
    }

    /**
     * 创建任务并保存其关联简历文本
     */
    @Transactional
    public TaskUploadResponseDTO createTaskAndSaveResumes(List<ResumeTextDTO> resumeTexts) {
        LocalDateTime now = LocalDateTime.now();
        TaskDO task = new TaskDO();
        task.setTaskId(UUID.randomUUID().toString());
        task.setResumeCount(0);
        task.setSubmitted(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskDAO.insert(task);

        List<ResumeTextDO> savedResumes = resumeTextService.saveAllAndReturn(resumeTexts);
        for (ResumeTextDO savedResume : savedResumes) {
            TaskResumeDO relation = new TaskResumeDO();
            relation.setTaskId(task.getId());
            relation.setResumeTextId(savedResume.getId());
            taskResumeDAO.insert(relation);
        }

        int savedCount = savedResumes.size();

        TaskDO update = new TaskDO();
        update.setId(task.getId());
        update.setResumeCount(savedCount);
        update.setUpdateTime(LocalDateTime.now());
        taskDAO.updateById(update);

        TaskUploadResponseDTO response = new TaskUploadResponseDTO();
        response.setTaskId(task.getTaskId());
        response.setResumeCount(savedCount);
        response.setSavedCount(savedCount);
        return response;
    }

    public TaskDO getByBusinessTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskDAO.selectOne(new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getTaskId, taskId)
                .last("LIMIT 1"));
    }

    public List<TaskDO> listAll() {
        return taskDAO.selectList(new LambdaQueryWrapper<TaskDO>()
                .orderByDesc(TaskDO::getCreateTime)
                .orderByDesc(TaskDO::getId));
    }

    public TaskDO getById(Long id) {
        if (id == null) {
            return null;
        }
        return taskDAO.selectById(id);
    }

    public boolean bindPythonTaskId(Long taskDbId, String pythonTaskId) {
        if (taskDbId == null || pythonTaskId == null || pythonTaskId.isBlank()) {
            return false;
        }
        int affected = taskDAO.update(null, new LambdaUpdateWrapper<TaskDO>()
                .eq(TaskDO::getId, taskDbId)
                .set(TaskDO::getPythonTaskId, pythonTaskId)
                .set(TaskDO::getUpdateTime, LocalDateTime.now()));
        return affected > 0;
    }

    /**
     * 更新任务的 submitted 字段（提交成功时置为 1，失败则不调用此方法保持 0）
     */
    public boolean setSubmitted(Long taskDbId, int submitted) {
        if (taskDbId == null) {
            return false;
        }
        int affected = taskDAO.update(null, new LambdaUpdateWrapper<TaskDO>()
                .eq(TaskDO::getId, taskDbId)
                .set(TaskDO::getSubmitted, submitted)
                .set(TaskDO::getUpdateTime, LocalDateTime.now()));
        return affected > 0;
    }
}
