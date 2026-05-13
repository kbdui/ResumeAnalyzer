package com.app.service;

import com.app.dao.TaskResumeDAO;
import com.app.dao.TextDAO;
import com.app.dto.ResumeDTO;
import com.app.dto.TextDTO;
import com.app.dto.resumeDetail.EducationDTO;
import com.app.dto.resumeDetail.ProjectDTO;
import com.app.dto.resumeDetail.WorkExperienceDTO;
import com.app.entity.HybridResultDO;
import com.app.entity.JdExtractDO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.app.entity.TextDO;
import com.app.request.ResumeHybridRequest;
import com.app.service.repository.HybridResultService;
import com.app.service.repository.JdExtractService;
import com.app.service.repository.ResumeService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ResumeHybridService {

    private final TaskService taskService;
    private final TextDAO textDAO;
    private final TaskResumeDAO taskResumeDAO;
    private final PythonService pythonService;
    private final DeepseekBaseService deepseekBaseService;
    private final JdExtractService jdExtractService;
    private final HybridResultService hybridResultService;
    private final ResumeService resumeService;

    @Value("${deepseek.api.key}")
    private String deepseekApiKey;

    public ResumeHybridService(TaskService taskService,
                               TextDAO textDAO,
                               TaskResumeDAO taskResumeDAO,
                               PythonService pythonService,
                               DeepseekBaseService deepseekBaseService,
                               JdExtractService jdExtractService,
                               HybridResultService hybridResultService,
                               ResumeService resumeService) {
        this.taskService = taskService;
        this.textDAO = textDAO;
        this.taskResumeDAO = taskResumeDAO;
        this.pythonService = pythonService;
        this.deepseekBaseService = deepseekBaseService;
        this.jdExtractService = jdExtractService;
        this.hybridResultService = hybridResultService;
        this.resumeService = resumeService;
    }

    /**
     * 按业务 taskId 提交 Python 匹配任务，返回 pythonTaskId。
     */
    public String submitByTaskId(String taskId, String jdText, Integer topK, Integer recallK) throws IOException {
        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("jdText 不能为空");
        }
        String normalizedJdText = jdText.trim();

        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }

        List<TaskResumeDO> passedRows = taskResumeDAO.selectList(new LambdaQueryWrapper<TaskResumeDO>()
                .eq(TaskResumeDO::getTaskId, task.getId())
                .eq(TaskResumeDO::getPass, Boolean.TRUE)
                .orderByAsc(TaskResumeDO::getId));
        if (passedRows == null || passedRows.isEmpty()) {
            throw new IllegalArgumentException("task 下没有通过硬过滤的简历(task_resume.pass=1): " + taskId);
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
            dto.setRawText(textRow.getText());
            dto.setHardFilterResult("PASS");

            ResumeDTO resumeDetail = resumeService.getResumeDetailByBusinessResumeId(rid);
            if (resumeDetail != null) {
                dto.setSkills(resumeDetail.getSkills());
                dto.setWorkExperience(resumeDetail.getWorkExperience());
                dto.setProjects(resumeDetail.getProjects());
                dto.setEducation(resumeDetail.getEducation());
                dto.setSummary(buildSummary(resumeDetail));
                dto.setRoleTags(extractRoleTags(resumeDetail.getWorkExperience()));
                dto.setIndustryTags(extractIndustryTags(resumeDetail));
                dto.setManagementLevel(inferManagementLevel(resumeDetail.getWorkExperience()));
                dto.setYearsOfExperience(estimateYearsOfExperience(resumeDetail.getWorkExperience()));
                dto.setKeywords(buildKeywords(resumeDetail));
            }
            resumes.add(dto);
        }
        if (resumes.isEmpty()) {
            throw new IllegalArgumentException("通过硬过滤的简历在 text 表中无有效正文: " + taskId);
        }

        JdExtractDO jdExtract = resolveJdExtract(task.getId(), normalizedJdText);
        if (jdExtract == null) {
            throw new IllegalStateException("JD 关键词提取结果保存失败");
        }

        ResumeHybridRequest request = new ResumeHybridRequest();
        request.setTaskId(taskId);
        request.setJdText(jdExtract.getJdText());
        request.setWorkExperienceKeywords(jdExtract.getWorkExperienceKeywords());
        request.setSkillsKeywords(jdExtract.getSkillsKeywords());
        request.setEducationKeywords(jdExtract.getEducationKeywords());
        request.setProjectKeywords(buildProjectKeywords(jdExtract));
        request.setPreferredRoles(buildPreferredRoles(jdExtract));
        request.setPreferredIndustries(buildPreferredIndustries(normalizedJdText));
        request.setResumes(resumes);
        request.setTopK(topK == null ? 20 : topK);
        request.setRecallK(recallK == null ? 200 : recallK);

        String pythonTaskId = pythonService.submitHybridTask(request);
        taskService.bindPythonTaskId(task.getId(), pythonTaskId);
        taskService.setSubmitted(task.getId(), 1);
        return pythonTaskId;
    }

    private JdExtractDO resolveJdExtract(Long taskDbId, String jdText) throws IOException {
        JdExtractDO existing = jdExtractService.getByTaskId(taskDbId);
        if (isUsableJdExtract(existing, jdText)) {
            return existing;
        }

        JsonNode keywordRoot = deepseekBaseService.extractHybridJdKeywords(deepseekApiKey, jdText, true);
        String workKeywords = keywordRoot.path("work_experience_keywords").asText("").trim();
        String skillsKeywords = keywordRoot.path("skills_keywords").asText("").trim();
        String educationKeywords = keywordRoot.path("education_keywords").asText("").trim();
        return jdExtractService.upsertByTaskId(
                taskDbId,
                jdText,
                workKeywords,
                skillsKeywords,
                educationKeywords
        );
    }

    private boolean isUsableJdExtract(JdExtractDO jdExtract, String jdText) {
        if (jdExtract == null) {
            return false;
        }
        if (jdExtract.getJdText() == null || !jdText.equals(jdExtract.getJdText().trim())) {
            return false;
        }
        return hasText(jdExtract.getWorkExperienceKeywords())
                || hasText(jdExtract.getSkillsKeywords())
                || hasText(jdExtract.getEducationKeywords());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildSummary(ResumeDTO resumeDetail) {
        List<String> parts = new ArrayList<>();
        if (resumeDetail.getPersonalInfo() != null && hasText(resumeDetail.getPersonalInfo().getName())) {
            parts.add(resumeDetail.getPersonalInfo().getName());
        }
        if (resumeDetail.getSkills() != null && !resumeDetail.getSkills().isEmpty()) {
            parts.add(String.join(" ", resumeDetail.getSkills()));
        }
        if (resumeDetail.getWorkExperience() != null && !resumeDetail.getWorkExperience().isEmpty()) {
            WorkExperienceDTO first = resumeDetail.getWorkExperience().get(0);
            if (first != null) {
                parts.add(joinNonBlank(first.getPosition(), first.getCompany(), first.getDescription()));
            }
        }
        return joinNonBlank(parts.toArray(new String[0]));
    }

    private List<String> buildKeywords(ResumeDTO resumeDetail) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (resumeDetail.getSkills() != null) {
            tags.addAll(resumeDetail.getSkills());
        }
        if (resumeDetail.getProjects() != null) {
            for (ProjectDTO project : resumeDetail.getProjects()) {
                if (project == null) {
                    continue;
                }
                addIfPresent(tags, project.getName());
                if (project.getTechnologies() != null) {
                    tags.addAll(project.getTechnologies());
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private List<String> extractRoleTags(List<WorkExperienceDTO> workExperiences) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (workExperiences == null) {
            return new ArrayList<>();
        }
        for (WorkExperienceDTO work : workExperiences) {
            if (work == null) {
                continue;
            }
            addIfPresent(tags, work.getPosition());
        }
        return new ArrayList<>(tags);
    }

    private List<String> extractIndustryTags(ResumeDTO resumeDetail) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (resumeDetail.getWorkExperience() != null) {
            for (WorkExperienceDTO work : resumeDetail.getWorkExperience()) {
                if (work == null) {
                    continue;
                }
                addIndustryHints(tags, work.getCompany());
                addIndustryHints(tags, work.getPosition());
                addIndustryHints(tags, work.getDescription());
            }
        }
        if (resumeDetail.getProjects() != null) {
            for (ProjectDTO project : resumeDetail.getProjects()) {
                if (project == null) {
                    continue;
                }
                addIndustryHints(tags, project.getName());
                addIndustryHints(tags, project.getDescription());
            }
        }
        return new ArrayList<>(tags);
    }

    private void addIndustryHints(Set<String> tags, String text) {
        if (!hasText(text)) {
            return;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("市场") || normalized.contains("营销") || normalized.contains("品牌") || normalized.contains("传播")) {
            tags.add("市场营销");
        }
        if (normalized.contains("销售") || normalized.contains("客户")) {
            tags.add("销售");
        }
        if (normalized.contains("银行") || normalized.contains("金融")) {
            tags.add("金融");
        }
        if (normalized.contains("教育") || normalized.contains("教师") || normalized.contains("学校")) {
            tags.add("教育");
        }
        if (normalized.contains("软件") || normalized.contains("开发") || normalized.contains("测试")) {
            tags.add("软件");
        }
    }

    private String inferManagementLevel(List<WorkExperienceDTO> workExperiences) {
        if (workExperiences == null) {
            return null;
        }
        for (WorkExperienceDTO work : workExperiences) {
            if (work == null || !hasText(work.getPosition())) {
                continue;
            }
            String position = work.getPosition().toLowerCase(Locale.ROOT);
            if (position.contains("总监") || position.contains("director")) {
                return "director";
            }
            if (position.contains("经理") || position.contains("manager")) {
                return "manager";
            }
            if (position.contains("主管") || position.contains("lead") || position.contains("高级")) {
                return "senior";
            }
        }
        return null;
    }

    private Double estimateYearsOfExperience(List<WorkExperienceDTO> workExperiences) {
        if (workExperiences == null || workExperiences.isEmpty()) {
            return null;
        }
        return (double) workExperiences.size();
    }

    private String buildProjectKeywords(JdExtractDO jdExtract) {
        return joinNonBlank(
                jdExtract.getWorkExperienceKeywords(),
                jdExtract.getSkillsKeywords()
        );
    }

    private String buildPreferredRoles(JdExtractDO jdExtract) {
        return joinNonBlank(
                jdExtract.getWorkExperienceKeywords(),
                jdExtract.getSkillsKeywords()
        );
    }

    private String buildPreferredIndustries(String jdText) {
        if (!hasText(jdText)) {
            return "";
        }
        String text = jdText.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> industries = new LinkedHashSet<>();
        if (text.contains("市场") || text.contains("营销") || text.contains("品牌")) {
            industries.add("市场营销");
        }
        if (text.contains("银行") || text.contains("金融")) {
            industries.add("金融");
        }
        if (text.contains("软件") || text.contains("开发")) {
            industries.add("软件");
        }
        if (text.contains("教育") || text.contains("培训")) {
            industries.add("教育");
        }
        return String.join(" ", industries);
    }

    private void addIfPresent(Set<String> target, String text) {
        if (hasText(text)) {
            target.add(text.trim());
        }
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    /**
     * 查询 Python 任务，并按约定维护 hybrid_result。
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
