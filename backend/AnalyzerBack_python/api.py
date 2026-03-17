"""
路由
"""

from fastapi import FastAPI
import uuid

from schemas import MatchPipelineRequest
from service import run_pipeline, _now
from worker import TASK_LOCK, TASK_STORE, TASK_QUEUE, start_worker

app = FastAPI(title="Resume Matching Service")


@app.on_event("startup")
def startup_event():
    start_worker()


@app.post("/match/pipeline")
def match_pipeline(req: MatchPipelineRequest):
    """同步 pipeline 匹配接口：直接返回召回+重排结果。"""
    return run_pipeline(req)


@app.post("/tasks/match-pipeline")
def create_match_task(req: MatchPipelineRequest):
    """
    创建异步 pipeline 任务。

    返回：
    - task_id：用于后续查询
    - status：初始为 queued
    """
    task_id = str(uuid.uuid4())
    with TASK_LOCK:
        TASK_STORE[task_id] = {
            "task_id": task_id,
            "status": "queued",
            "created_at": _now(),
            "request": req.model_dump(),
        }
    TASK_QUEUE.put(task_id)
    return {"task_id": task_id, "status": "queued"}


@app.get("/tasks/{task_id}")
def get_task(task_id: str):
    """
    查询异步任务状态与结果。

    - not_found：任务不存在（或服务重启后内存丢失）
    - queued/running/done/failed：返回对应时间戳、错误信息与结果（若已完成）
    """
    with TASK_LOCK:
        task = TASK_STORE.get(task_id)
        if task is None:
            return {"task_id": task_id, "status": "not_found"}
        return {
            "task_id": task_id,
            "status": task["status"],
            "created_at": task.get("created_at"),
            "started_at": task.get("started_at"),
            "ended_at": task.get("ended_at"),
            "error": task.get("error"),
            "result": task.get("result"),
        }
