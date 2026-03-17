import queue
import threading
from typing import Dict, Any

from schemas import MatchPipelineRequest
from service import _now, run_pipeline

TASK_QUEUE: queue.Queue[str] = queue.Queue()
TASK_STORE: Dict[str, Dict[str, Any]] = {}
TASK_LOCK = threading.Lock()

_worker_thread = None


def _worker():
    while True:
        task_id = TASK_QUEUE.get()
        with TASK_LOCK:
            task = TASK_STORE.get(task_id)
            if task is None:
                TASK_QUEUE.task_done()
                continue
            task["status"] = "running"
            task["started_at"] = _now()
            req_dict = task["request"]
        try:
            req = MatchPipelineRequest.model_validate(req_dict)
            result = run_pipeline(req)
            with TASK_LOCK:
                task["status"] = "done"
                task["result"] = result
                task["ended_at"] = _now()
        except Exception as e:
            with TASK_LOCK:
                task["status"] = "failed"
                task["error"] = str(e)
                task["ended_at"] = _now()
        finally:
            TASK_QUEUE.task_done()


def start_worker():
    global _worker_thread
    if _worker_thread is None or not _worker_thread.is_alive():
        _worker_thread = threading.Thread(target=_worker, daemon=True)
        _worker_thread.start()