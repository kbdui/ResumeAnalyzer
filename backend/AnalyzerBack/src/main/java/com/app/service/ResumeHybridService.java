package com.app.service;

import com.app.dao.TaskResumeDAO;
import com.app.dao.TextDAO;
import com.app.dto.TextDTO;
import com.app.entity.HybridResultDO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.app.entity.TextDO;
import com.app.service.repository.HybridResultService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeHybridService {

    private final TaskService taskService;
    private final TextDAO textDAO;
    private final TaskResumeDAO taskResumeDAO;
    private final PythonService pythonService;
    private final HybridResultService hybridResultService;

    public ResumeHybridService(TaskService taskService,
                               TextDAO textDAO,
                               TaskResumeDAO taskResumeDAO,
                               PythonService pythonService,
                               HybridResultService hybridResultService) {
        this.taskService = taskService;
        this.textDAO = textDAO;
        this.taskResumeDAO = taskResumeDAO;
        this.pythonService = pythonService;
        this.hybridResultService = hybridResultService;
    }

    /**
     * 按业务 taskId 提交 Python 匹配任务，返回 pythonTaskId
     */
    public String submitByTaskId(String taskId, String jdText, Integer topK, Integer recallK) throws IOException {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }

        List<TaskResumeDO> passedRows = taskResumeDAO.selectList(new LambdaQueryWrapper<TaskResumeDO>()
                .eq(TaskResumeDO::getTaskId, task.getId())
                .eq(TaskResumeDO::getPass, Boolean.TRUE)
                .orderByAsc(TaskResumeDO::getId));
        if (passedRows == null || passedRows.isEmpty()) {
            throw new IllegalArgumentException("task 下没有通过硬过滤的简历（task_resume.pass=1）: " + taskId);
        }

        List<TextDTO> resumes = new ArrayList<>(passedRows.size());
        for (TaskResumeDO tr : passedRows) {
            if (tr == null || tr.getResumeId() == null || tr.getResumeId().isBlank()) {
                continue;
            }
            String rid = tr.getResumeId().trim();
            TextDO textRow = textDAO.selectOne(new LambdaQueryWrapper<TextDO>()
                    .eq(TextDO::getTaskId, task.getId())
                    .eq(TextDO::getResumeId, rid)
                    .last("LIMIT 1"));
            if (textRow == null || textRow.getText() == null || textRow.getText().isBlank()) {
                continue;
            }
            TextDTO dto = new TextDTO();
            dto.setResumeId(textRow.getResumeId());
            dto.setFileName(textRow.getFileName());
            dto.setText(textRow.getText());
            resumes.add(dto);
        }
        if (resumes.isEmpty()) {
            throw new IllegalArgumentException("通过硬过滤的简历在 text 表中无有效正文: " + taskId);
        }

        String pythonTaskId = pythonService.submitHybridTask(jdText, resumes, topK, recallK);
        taskService.bindPythonTaskId(task.getId(), pythonTaskId);
        taskService.setSubmitted(task.getId(), 1);
        return pythonTaskId;
    }

    /**
     * 查询 Python 任务，并按约定维护 hybrid_result
     */
    public JsonNode queryAndStoreResult(String taskId) throws IOException {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }
        if (task.getPythonTaskId() == null || task.getPythonTaskId().isBlank()) {
            throw new IllegalArgumentException("task 尚未绑定 Python 任务ID: " + taskId);
        }
        JsonNode result = pythonService.getTask(task.getPythonTaskId());
        hybridResultService.upsertFromPythonResult(task.getId(), result);
        return result;
    }

    public HybridResultDO getStoredResult(String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return null;
        }
        return hybridResultService.getByTaskId(task.getId());
    }
}
