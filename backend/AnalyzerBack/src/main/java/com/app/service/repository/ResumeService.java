package com.app.service.repository;

import com.app.dao.ResumeDAO;
import com.app.dao.resumeDetail.EducationDAO;
import com.app.dao.resumeDetail.PersonalInfoDAO;
import com.app.dao.resumeDetail.ProjectDAO;
import com.app.dao.resumeDetail.WorkExperienceDAO;
import com.app.dto.ResumeDTO;
import com.app.dto.resumeDetail.EducationDTO;
import com.app.dto.resumeDetail.PersonalInfoDTO;
import com.app.dto.resumeDetail.ProjectDTO;
import com.app.dto.resumeDetail.WorkExperienceDTO;
import com.app.entity.ResumeDO;
import com.app.entity.resumeDetail.EducationDO;
import com.app.entity.resumeDetail.PersonalInfoDO;
import com.app.entity.resumeDetail.ProjectDO;
import com.app.entity.resumeDetail.WorkExperienceDO;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简历持久化服务：将解析后的 ResumeDTO 列表保存到数据库
 */
@Service
public class ResumeService {

    private final ResumeDAO resumeDAO;
    private final PersonalInfoDAO personalInfoDAO;
    private final EducationDAO educationDAO;
    private final WorkExperienceDAO workExperienceDAO;
    private final ProjectDAO projectDAO;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeDAO resumeDAO,
                         PersonalInfoDAO personalInfoDAO,
                         EducationDAO educationDAO,
                         WorkExperienceDAO workExperienceDAO,
                         ProjectDAO projectDAO,
                         ObjectMapper objectMapper) {
        this.resumeDAO = resumeDAO;
        this.personalInfoDAO = personalInfoDAO;
        this.educationDAO = educationDAO;
        this.workExperienceDAO = workExperienceDAO;
        this.projectDAO = projectDAO;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量保存简历解析结果
     */
    @Transactional
    public void saveResumes(List<ResumeDTO> resumes) {
        if (resumes == null || resumes.isEmpty()) {
            return;
        }
        for (ResumeDTO resumeDTO : resumes) {
            saveSingleResume(resumeDTO, null);
        }
    }

    /**
     * 保存单个简历解析结果
     */
    @Transactional
    public void saveResume(ResumeDTO resumeDTO) {
        if (resumeDTO == null) {
            return;
        }
        saveSingleResume(resumeDTO, null);
    }

    /**
     * 保存单个简历解析结果并返回主表ID
     */
    @Transactional
    public Long saveResumeAndReturnId(ResumeDTO resumeDTO) {
        if (resumeDTO == null) {
            return null;
        }
        return saveSingleResume(resumeDTO, null);
    }

    /**
     * 保存单个简历解析结果并返回主表 ID，可指定业务 resume_id（与 text.resume_id 对齐）
     */
    @Transactional
    public Long saveResumeAndReturnId(ResumeDTO resumeDTO, String businessResumeId) {
        if (resumeDTO == null) {
            return null;
        }
        return saveSingleResume(resumeDTO, businessResumeId);
    }

    /**
     * 保存单个简历及其明细
     */
    private Long saveSingleResume(ResumeDTO dto, String businessResumeIdOverride) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 保存主表 resume
        ResumeDO resumeDO = new ResumeDO();
        resumeDO.setCreateTime(now);
        resumeDO.setUpdateTime(now);

        String businessResumeId = businessResumeIdOverride;
        if (businessResumeId == null || businessResumeId.isBlank()) {
            businessResumeId = dto.getResumeId();
        }
        if (businessResumeId != null && !businessResumeId.isBlank()) {
            resumeDO.setResumeId(businessResumeId.trim());
        }

        // skills / certificates 使用 JSON 字符串存储
        if (dto.getSkills() != null) {
            resumeDO.setSkills(toJson(dto.getSkills()));
        }
        if (dto.getCertificates() != null) {
            resumeDO.setCertificates(toJson(dto.getCertificates()));
        }

        resumeDAO.insert(resumeDO);
        Long resumeId = resumeDO.getId();
        if (resumeId == null) {
            // 未获取到自增主键，直接返回避免 NPE
            return null;
        }

        // 2. 个人信息
        PersonalInfoDTO personalInfoDTO = dto.getPersonalInfo();
        if (personalInfoDTO != null) {
            PersonalInfoDO infoDO = new PersonalInfoDO();
            infoDO.setResumeId(resumeId);
            infoDO.setName(personalInfoDTO.getName());
            infoDO.setContact(personalInfoDTO.getContact());
            infoDO.setEmail(personalInfoDTO.getEmail());
            infoDO.setCreateTime(now);
            infoDO.setUpdateTime(now);
            personalInfoDAO.insert(infoDO);
        }

        // 3. 教育经历
        List<EducationDTO> educationList = dto.getEducation();
        if (educationList != null && !educationList.isEmpty()) {
            for (int i = 0; i < educationList.size(); i++) {
                EducationDTO eduDTO = educationList.get(i);
                EducationDO eduDO = new EducationDO();
                eduDO.setResumeId(resumeId);
                eduDO.setSchool(eduDTO.getSchool());
                eduDO.setMajor(eduDTO.getMajor());
                eduDO.setDegree(eduDTO.getDegree());
                eduDO.setGraduationYear(eduDTO.getGraduationYear());
                eduDO.setSortOrder(i);
                eduDO.setCreateTime(now);
                eduDO.setUpdateTime(now);
                educationDAO.insert(eduDO);
            }
        }

        // 4. 工作经历
        List<WorkExperienceDTO> workList = dto.getWorkExperience();
        if (workList != null && !workList.isEmpty()) {
            for (int i = 0; i < workList.size(); i++) {
                WorkExperienceDTO wDTO = workList.get(i);
                WorkExperienceDO wDO = new WorkExperienceDO();
                wDO.setResumeId(resumeId);
                wDO.setCompany(wDTO.getCompany());
                wDO.setPosition(wDTO.getPosition());
                wDO.setDuration(wDTO.getDuration());
                wDO.setDescription(wDTO.getDescription());
                wDO.setSortOrder(i);
                wDO.setCreateTime(now);
                wDO.setUpdateTime(now);
                workExperienceDAO.insert(wDO);
            }
        }

        // 5. 项目经历
        List<ProjectDTO> projectList = dto.getProjects();
        if (projectList != null && !projectList.isEmpty()) {
            for (int i = 0; i < projectList.size(); i++) {
                ProjectDTO pDTO = projectList.get(i);
                ProjectDO pDO = new ProjectDO();
                pDO.setResumeId(resumeId);
                pDO.setName(pDTO.getName());
                pDO.setDescription(pDTO.getDescription());
                if (pDTO.getTechnologies() != null) {
                    pDO.setTechnologies(toJson(pDTO.getTechnologies()));
                }
                pDO.setSortOrder(i);
                pDO.setCreateTime(now);
                pDO.setUpdateTime(now);
                projectDAO.insert(pDO);
            }
        }
        return resumeId;
    }

    /**
     * 根据简历主表 ID 删除简历及其所有明细
     * 依赖数据库外键的 ON DELETE CASCADE 配置
     */
    @Transactional
    public void deleteResumeById(Long resumeId) {
        if (resumeId == null) {
            return;
        }
        resumeDAO.deleteById(resumeId);
    }

    public ResumeDTO getResumeDetailById(Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        ResumeDO resumeDO = resumeDAO.selectById(resumeId);
        if (resumeDO == null) {
            return null;
        }
        return toResumeDTO(resumeDO);
    }

    /**
     * 按业务 resume_id（resume 表 resume_id 列）加载完整简历 DTO
     */
    public ResumeDTO getResumeDetailByBusinessResumeId(String businessResumeId) {
        if (businessResumeId == null || businessResumeId.isBlank()) {
            return null;
        }
        ResumeDO resumeDO = resumeDAO.selectOne(new LambdaQueryWrapper<ResumeDO>()
                .eq(ResumeDO::getResumeId, businessResumeId.trim())
                .last("LIMIT 1"));
        if (resumeDO == null) {
            return null;
        }
        return toResumeDTO(resumeDO);
    }

    private ResumeDTO toResumeDTO(ResumeDO resumeDO) {
        ResumeDTO dto = new ResumeDTO();
        dto.setResumeId(resumeDO.getResumeId());
        dto.setSkills(parseStringList(resumeDO.getSkills()));
        dto.setCertificates(parseStringList(resumeDO.getCertificates()));

        PersonalInfoDO personalInfoDO = personalInfoDAO.selectOne(new LambdaQueryWrapper<PersonalInfoDO>()
                .eq(PersonalInfoDO::getResumeId, resumeDO.getId())
                .last("LIMIT 1"));
        if (personalInfoDO != null) {
            PersonalInfoDTO personalInfoDTO = new PersonalInfoDTO();
            personalInfoDTO.setName(personalInfoDO.getName());
            personalInfoDTO.setContact(personalInfoDO.getContact());
            personalInfoDTO.setEmail(personalInfoDO.getEmail());
            dto.setPersonalInfo(personalInfoDTO);
        }

        List<EducationDO> educationDOS = educationDAO.selectList(new LambdaQueryWrapper<EducationDO>()
                .eq(EducationDO::getResumeId, resumeDO.getId())
                .orderByAsc(EducationDO::getSortOrder)
                .orderByAsc(EducationDO::getId));
        List<EducationDTO> educationDTOS = new ArrayList<>();
        for (EducationDO educationDO : educationDOS) {
            EducationDTO educationDTO = new EducationDTO();
            educationDTO.setSchool(educationDO.getSchool());
            educationDTO.setMajor(educationDO.getMajor());
            educationDTO.setDegree(educationDO.getDegree());
            educationDTO.setGraduationYear(educationDO.getGraduationYear());
            educationDTOS.add(educationDTO);
        }
        dto.setEducation(educationDTOS);

        List<WorkExperienceDO> workExperienceDOS = workExperienceDAO.selectList(new LambdaQueryWrapper<WorkExperienceDO>()
                .eq(WorkExperienceDO::getResumeId, resumeDO.getId())
                .orderByAsc(WorkExperienceDO::getSortOrder)
                .orderByAsc(WorkExperienceDO::getId));
        List<WorkExperienceDTO> workExperienceDTOS = new ArrayList<>();
        for (WorkExperienceDO workExperienceDO : workExperienceDOS) {
            WorkExperienceDTO workExperienceDTO = new WorkExperienceDTO();
            workExperienceDTO.setCompany(workExperienceDO.getCompany());
            workExperienceDTO.setPosition(workExperienceDO.getPosition());
            workExperienceDTO.setDuration(workExperienceDO.getDuration());
            workExperienceDTO.setDescription(workExperienceDO.getDescription());
            workExperienceDTOS.add(workExperienceDTO);
        }
        dto.setWorkExperience(workExperienceDTOS);

        List<ProjectDO> projectDOS = projectDAO.selectList(new LambdaQueryWrapper<ProjectDO>()
                .eq(ProjectDO::getResumeId, resumeDO.getId())
                .orderByAsc(ProjectDO::getSortOrder)
                .orderByAsc(ProjectDO::getId));
        List<ProjectDTO> projectDTOS = new ArrayList<>();
        for (ProjectDO projectDO : projectDOS) {
            ProjectDTO projectDTO = new ProjectDTO();
            projectDTO.setName(projectDO.getName());
            projectDTO.setDescription(projectDO.getDescription());
            projectDTO.setTechnologies(parseStringList(projectDO.getTechnologies()));
            projectDTOS.add(projectDTO);
        }
        dto.setProjects(projectDTOS);
        return dto;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            // JSON 转换失败时退化为 toString，避免整个事务失败
            return String.valueOf(obj);
        }
    }
}

