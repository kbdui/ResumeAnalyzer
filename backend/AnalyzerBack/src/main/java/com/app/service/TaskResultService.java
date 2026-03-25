package com.app.service;

import com.app.dao.TaskResultDAO;
import com.app.entity.TaskResultDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class TaskResultService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final TaskResultDAO taskResultDAO;
    private final ObjectMapper objectMapper;

    public TaskResultService(TaskResultDAO taskResultDAO, ObjectMapper objectMapper) {
        this.taskResultDAO = taskResultDAO;
        this.objectMapper = objectMapper;
    }

    public TaskResultDO getByTaskId(Long taskDbId) {
        if (taskDbId == null) {
            return null;
        }
        return taskResultDAO.selectOne(new LambdaQueryWrapper<TaskResultDO>()
                .eq(TaskResultDO::getTaskId, taskDbId)
                .last("LIMIT 1"));
    }

    public TaskResultDO getById(Long id) {
        if (id == null) {
            return null;
        }
        return taskResultDAO.selectById(id);
    }

    public List<TaskResultDO> listByTaskId(Long taskDbId) {
        if (taskDbId == null) {
            return Collections.emptyList();
        }
        return taskResultDAO.selectList(new LambdaQueryWrapper<TaskResultDO>()
                .eq(TaskResultDO::getTaskId, taskDbId)
                .orderByDesc(TaskResultDO::getUpdateTime)
                .orderByDesc(TaskResultDO::getId));
    }

    /**
     * 查询后写入 task_result：
     * - 若不存在则创建
     * - 若已成功/失败则不再重复写入
     * - 若进行中则更新为最新状态
     */
    @Transactional
    public TaskResultDO upsertFromPythonResult(Long taskDbId, JsonNode pythonResult) {
        String status = resolveStatus(pythonResult);
        String resultJson = toJson(pythonResult);
        LocalDateTime now = LocalDateTime.now();

        TaskResultDO existing = getByTaskId(taskDbId);
        if (existing == null) {
            TaskResultDO created = new TaskResultDO();
            created.setTaskId(taskDbId);
            created.setStatus(status);
            created.setResultJson(resultJson);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            taskResultDAO.insert(created);
            return created;
        }

        if (isTerminal(existing.getStatus())) {
            return existing;
        }

        TaskResultDO update = new TaskResultDO();
        update.setId(existing.getId());
        update.setStatus(status);
        update.setResultJson(resultJson);
        update.setUpdateTime(now);
        taskResultDAO.updateById(update);
        return taskResultDAO.selectById(existing.getId());
    }

    private boolean isTerminal(String status) {
        return STATUS_SUCCESS.equalsIgnoreCase(status) || STATUS_FAILED.equalsIgnoreCase(status);
    }

    private String resolveStatus(JsonNode root) {
        if (root == null || root.isNull()) {
            return STATUS_RUNNING;
        }
        String rawStatus = root.path("status").asText("");
        String normalized = rawStatus.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return STATUS_RUNNING;
        }
        if (normalized.contains("success")
                || normalized.contains("succeeded")
                || normalized.contains("complete")
                || normalized.contains("completed")
                || normalized.contains("done")) {
            return STATUS_SUCCESS;
        }
        if (normalized.contains("fail")
                || normalized.contains("failed")
                || normalized.contains("error")
                || normalized.contains("cancel")) {
            return STATUS_FAILED;
        }
        return STATUS_RUNNING;
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            return String.valueOf(node);
        }
    }
}
