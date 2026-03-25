package com.app.service;

import com.app.dao.ResumeTextDAO;
import com.app.dao.TaskResumeDAO;
import com.app.dto.ResumeTextDTO;
import com.app.entity.ResumeTextDO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResultDO;
import com.app.entity.TaskResumeDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskMatchService {

    private final TaskService taskService;
    private final ResumeTextDAO resumeTextDAO;
    private final TaskResumeDAO taskResumeDAO;
    private final PythonService pythonService;
    private final TaskResultService taskResultService;

    public TaskMatchService(TaskService taskService,
                            ResumeTextDAO resumeTextDAO,
                            TaskResumeDAO taskResumeDAO,
                            PythonService pythonService,
                            TaskResultService taskResultService) {
        this.taskService = taskService;
        this.resumeTextDAO = resumeTextDAO;
        this.taskResumeDAO = taskResumeDAO;
        this.pythonService = pythonService;
        this.taskResultService = taskResultService;
    }

    /**
     * 按业务 taskId 提交 Python 匹配任务，返回 pythonTaskId
     */
    public String submitByTaskId(String taskId, String jdText, Integer topK, Integer recallK) throws IOException {
        // 获得完整task
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }

         // 获得和该task关联的resumeID
        List<TaskResumeDO> relations = taskResumeDAO.selectList(new LambdaQueryWrapper<TaskResumeDO>()
                .eq(TaskResumeDO::getTaskId, task.getId())
                .orderByAsc(TaskResumeDO::getId));

        if (relations == null || relations.isEmpty()) {
            throw new IllegalArgumentException("task 下没有可提交的简历文本: " + taskId);
        }

        // 获得resumeID对应的resumeText
        List<Long> resumeTextIds = relations.stream()
                .map(TaskResumeDO::getResumeTextId)
                .collect(Collectors.toList());
        List<ResumeTextDO> rows = resumeTextDAO.selectBatchIds(resumeTextIds);

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("task 下没有可提交的简历文本: " + taskId);
        }

        // 以下两段对得到的resumeText排序
        Map<Long, ResumeTextDO> rowMap = rows.stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(ResumeTextDO::getId, item -> item, (a, b) -> a));

        List<ResumeTextDO> orderedRows = relations.stream()
                .map(relation -> rowMap.get(relation.getResumeTextId()))
                .filter(item -> item != null)
                .collect(Collectors.toList());

        // DO转为DTO
        List<ResumeTextDTO> resumes = new ArrayList<>(orderedRows.size());
        for (ResumeTextDO row : orderedRows) {
            if (row == null || row.getText() == null || row.getText().isBlank()) {
                continue;
            }
            ResumeTextDTO dto = new ResumeTextDTO();
            dto.setResumeId(row.getResumeId());
            dto.setFileName(row.getFileName());
            dto.setText(row.getText());
            resumes.add(dto);
        }
        if (resumes.isEmpty()) {
            throw new IllegalArgumentException("task 下没有有效简历文本: " + taskId);
        }

        // 提交任务，更新对应的PythonTaskId，设置任务已提交
        String pythonTaskId = pythonService.submitMatchTask(jdText, resumes, topK, recallK);
        taskService.bindPythonTaskId(task.getId(), pythonTaskId);
        taskService.setSubmitted(task.getId(), 1);
        return pythonTaskId;
    }

    /**
     * 查询 Python 任务，并按约定维护 task_result
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
        taskResultService.upsertFromPythonResult(task.getId(), result);
        return result;
    }

    public TaskResultDO getStoredResult(String taskId) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            return null;
        }
        return taskResultService.getByTaskId(task.getId());
    }
}
