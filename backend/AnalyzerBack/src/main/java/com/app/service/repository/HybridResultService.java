package com.app.service.repository;

import com.app.dao.HybridResultDAO;
import com.app.entity.HybridResultDO;
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
public class HybridResultService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final HybridResultDAO hybridResultDAO;
    private final ObjectMapper objectMapper;

    public HybridResultService(HybridResultDAO hybridResultDAO, ObjectMapper objectMapper) {
        this.hybridResultDAO = hybridResultDAO;
        this.objectMapper = objectMapper;
    }

    public HybridResultDO getByTaskId(Long taskDbId) {
        if (taskDbId == null) {
            return null;
        }
        return hybridResultDAO.selectOne(new LambdaQueryWrapper<HybridResultDO>()
                .eq(HybridResultDO::getTaskId, taskDbId)
                .last("LIMIT 1"));
    }

    public HybridResultDO getById(Long id) {
        if (id == null) {
            return null;
        }
        return hybridResultDAO.selectById(id);
    }

    public List<HybridResultDO> listByTaskId(Long taskDbId) {
        if (taskDbId == null) {
            return Collections.emptyList();
        }
        return hybridResultDAO.selectList(new LambdaQueryWrapper<HybridResultDO>()
                .eq(HybridResultDO::getTaskId, taskDbId)
                .orderByDesc(HybridResultDO::getUpdateTime)
                .orderByDesc(HybridResultDO::getId));
    }

    /**
     * 查询后写入 hybrid_result：不存在则创建；存在则始终覆盖为最新状态与结果。
     */
    @Transactional
    public HybridResultDO upsertFromPythonResult(Long taskDbId, JsonNode pythonResult) {
        String status = resolveStatus(pythonResult);
        String resultJson = toJson(pythonResult);
        LocalDateTime now = LocalDateTime.now();

        HybridResultDO existing = getByTaskId(taskDbId);
        if (existing == null) {
            HybridResultDO created = new HybridResultDO();
            created.setTaskId(taskDbId);
            created.setStatus(status);
            created.setResultJson(resultJson);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            hybridResultDAO.insert(created);
            return created;
        }

        HybridResultDO update = new HybridResultDO();
        update.setId(existing.getId());
        update.setStatus(status);
        update.setResultJson(resultJson);
        update.setUpdateTime(now);
        hybridResultDAO.updateById(update);
        return hybridResultDAO.selectById(existing.getId());
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
                || normalized.contains("not_found")
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
