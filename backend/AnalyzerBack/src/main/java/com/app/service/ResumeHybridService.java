package com.app.service;

import com.app.dao.TaskResumeDAO;
import com.app.dao.TextDAO;
import com.app.dto.TextDTO;
import com.app.entity.HybridResultDO;
import com.app.entity.JdExtractDO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.app.entity.TextDO;
import com.app.request.ResumeHybridRequest;
import com.app.service.repository.HybridResultService;
import com.app.service.repository.JdExtractService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
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
    private final DeepseekBaseService deepseekBaseService;
    private final JdExtractService jdExtractService;
    private final HybridResultService hybridResultService;

    @Value("${deepseek.api.key}")
    private String deepseekApiKey;

    public ResumeHybridService(TaskService taskService,
                               TextDAO textDAO,
                               TaskResumeDAO taskResumeDAO,
                               PythonService pythonService,
                               DeepseekBaseService deepseekBaseService,
                               JdExtractService jdExtractService,
                               HybridResultService hybridResultService) {
        this.taskService = taskService;
        this.textDAO = textDAO;
        this.taskResumeDAO = taskResumeDAO;
        this.pythonService = pythonService;
        this.deepseekBaseService = deepseekBaseService;
        this.jdExtractService = jdExtractService;
        this.hybridResultService = hybridResultService;
    }

    /**
     * 按业务 taskId 提交 Python 匹配任务，返回 pythonTaskId
     */
    public String submitByTaskId(String taskId, String jdText, Integer topK, Integer recallK) throws IOException {
        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("jdText 不能为空");
        }
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

        // 先通过 LLM 从 JD 中抽取结构化关键词并落库，再将关键词一并传给 Python 匹配服务。
        JsonNode keywordRoot = deepseekBaseService.extractHybridJdKeywords(deepseekApiKey, jdText.trim(), true);
        String workKeywords = keywordRoot.path("work_experience_keywords").asText("").trim();
        String skillsKeywords = keywordRoot.path("skills_keywords").asText("").trim();
        String educationKeywords = keywordRoot.path("education_keywords").asText("").trim();

        JdExtractDO jdExtract = jdExtractService.upsertByTaskId(
                task.getId(),
                jdText.trim(),
                workKeywords,
                skillsKeywords,
                educationKeywords
        );
        if (jdExtract == null) {
            throw new IllegalStateException("JD 关键词提取结果保存失败");
        }

        ResumeHybridRequest request = new ResumeHybridRequest();
        request.setTaskId(taskId);
        request.setJdText(jdExtract.getJdText());
        request.setWorkExperienceKeywords(jdExtract.getWorkExperienceKeywords());
        request.setSkillsKeywords(jdExtract.getSkillsKeywords());
        request.setEducationKeywords(jdExtract.getEducationKeywords());
        request.setResumes(resumes);
        request.setTopK(topK == null ? 20 : topK);
        request.setRecallK(recallK == null ? 200 : recallK);

        String pythonTaskId = pythonService.submitHybridTask(request);
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
