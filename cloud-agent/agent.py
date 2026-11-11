#!/usr/bin/env python3
"""Loopback-only Cloud Agent. All proposed actions require explicit approval."""
from __future__ import annotations
import json, os, secrets, sqlite3, subprocess, threading, time, uuid
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

HOST = "127.0.0.1"
PORT = int(os.environ.get("CLOUD_AGENT_PORT", "8765"))
DB_PATH = Path(os.environ.get("CLOUD_AGENT_DB", "/var/lib/cloud-agent/agent.db"))
API_KEY = os.environ.get("OPENAI_API_KEY", "")
MODEL = os.environ.get("OPENAI_MODEL", "")
ACCESS_TOKEN = os.environ.get("CLOUD_AGENT_TOKEN", "")
MAX_BODY, MAX_OUTPUT = 16_384, 12_000
LOCK = threading.Lock()

ACTIONS = {
    "disk_report": {"title": "Disk kullanımını incele", "command": ["df", "-hT", "--", "/"]},
    "memory_report": {"title": "RAM kullanımını incele", "command": ["free", "-h"]},
    "failed_services": {"title": "Başarısız servisleri listele", "command": ["systemctl", "--no-pager", "--failed"]},
    "recent_errors": {"title": "Son sistem hatalarını oku", "command": ["journalctl", "-p", "err", "-n", "30", "--no-pager"]},
}
PLAN_SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "string"},
        "actions": {"type": "array", "items": {
            "type": "object",
            "properties": {
                "action": {"type": "string", "enum": list(ACTIONS)},
                "reason": {"type": "string"},
            },
            "required": ["action", "reason"],
            "additionalProperties": False,
        }},
    },
    "required": ["summary", "actions"],
    "additionalProperties": False,
}

def connect_db():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("""CREATE TABLE IF NOT EXISTS tasks (
        id TEXT PRIMARY KEY, request TEXT NOT NULL, summary TEXT NOT NULL,
        actions_json TEXT NOT NULL, status TEXT NOT NULL,
        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, result_json TEXT
    )""")
    db.commit()
    return db

def response_text(payload):
    for item in payload.get("output", []):
        if item.get("type") == "message":
            for part in item.get("content", []):
                if part.get("type") == "output_text":
                    return part.get("text", "")
    raise ValueError("OpenAI yanıtında metin bulunamadı.")

def plan_with_openai(user_request):
    if not API_KEY or not MODEL:
        raise RuntimeError("OPENAI_API_KEY ve OPENAI_MODEL yapılandırılmalı.")
    body = {
        "model": MODEL, "store": False,
        "instructions": (
            "Sen Linux VM yöneten bir asistansın. İsteği analiz et ve yalnızca izin verilen "
            "salt-okunur inceleme eylemlerini öner. Komut, shell metni, dosya yolu veya serbest "
            "parametre üretme. Yalnızca şemadaki action değerlerini kullan. Her eylem insan "
            "onayı bekleyecek; hiçbir eylemi kendin çalıştırma. İstek desteklenmiyorsa actions boş olsun."
        ),
        "input": user_request,
        "text": {"format": {"type": "json_schema", "name": "cloud_agent_plan",
                            "strict": True, "schema": PLAN_SCHEMA}},
        "max_output_tokens": 1200,
    }
    request = Request("https://api.openai.com/v1/responses",
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        method="POST")
    try:
        with urlopen(request, timeout=45) as result:
            payload = json.loads(result.read(1_000_000).decode())
    except HTTPError as error:
        detail = error.read(2000).decode("utf-8", "replace")
        raise RuntimeError(f"OpenAI API HTTP {error.code}: {detail[:500]}") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError(f"OpenAI API bağlantı hatası: {error}") from error
    plan = json.loads(response_text(payload))
    if not isinstance(plan.get("summary"), str) or not isinstance(plan.get("actions"), list):
        raise ValueError("OpenAI planı beklenen biçimde değil.")
    actions = []
    for item in plan["actions"][:6]:
        name, reason = item.get("action"), item.get("reason")
        if name not in ACTIONS or not isinstance(reason, str):
            raise ValueError("Agent izin verilmeyen bir eylem önerdi.")
        actions.append({"action": name, "title": ACTIONS[name]["title"],
                        "reason": reason[:500], "status": "pending_approval", "result": None})
    return {"summary": plan["summary"][:2000], "actions": actions}

def run_approved_action(action_name):
    spec = ACTIONS.get(action_name)
    if not spec:
        raise ValueError("İzin verilmeyen eylem.")
    completed = subprocess.run(spec["command"], stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=20,
        check=False, env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LANG": "C.UTF-8"})
    output = completed.stdout[-MAX_OUTPUT:]
    return {"exit_code": completed.returncode, "output": output,
            "truncated": len(completed.stdout) > MAX_OUTPUT}

def json_response(handler, status, data):
    raw = json.dumps(data, ensure_ascii=False).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(raw)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(raw)

class Handler(BaseHTTPRequestHandler):
    server_version = "CloudAgent/0.1"
    def log_message(self, fmt, *args):
        print(f"[cloud-agent] {self.address_string()} {fmt % args}", flush=True)
    def authorized(self):
        return bool(ACCESS_TOKEN) and secrets.compare_digest(
            self.headers.get("Authorization", ""), f"Bearer {ACCESS_TOKEN}")
    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length < 2 or length > MAX_BODY:
            raise ValueError("İstek boyutu geçersiz.")
        value = json.loads(self.rfile.read(length).decode())
        if not isinstance(value, dict):
            raise ValueError("JSON nesnesi bekleniyor.")
        return value
    @staticmethod
    def task_dict(row, include_result=False):
        result = {"id": row["id"], "request": row["request"], "summary": row["summary"],
                  "actions": json.loads(row["actions_json"]), "status": row["status"],
                  "created_at": row["created_at"], "updated_at": row["updated_at"]}
        if include_result and row["result_json"]:
            result["result"] = json.loads(row["result_json"])
        return result
    def do_GET(self):
        if not self.authorized():
            return json_response(self, 401, {"error": "unauthorized"})
        if self.path == "/health":
            return json_response(self, 200, {"ok": True, "service": "cloud-agent",
                                             "model_configured": bool(MODEL)})
        if self.path == "/tasks":
            with connect_db() as db:
                rows = db.execute("SELECT * FROM tasks ORDER BY created_at DESC LIMIT 50").fetchall()
            return json_response(self, 200, {"tasks": [self.task_dict(row) for row in rows]})
        if self.path.startswith("/tasks/"):
            task_id = self.path.split("/", 2)[2]
            with connect_db() as db:
                row = db.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
            if row is None:
                return json_response(self, 404, {"error": "not_found"})
            return json_response(self, 200, self.task_dict(row, True))
        return json_response(self, 404, {"error": "not_found"})
    def do_POST(self):
        if not self.authorized():
            return json_response(self, 401, {"error": "unauthorized"})
        try:
            if self.path == "/tasks":
                payload = self.read_json()
                prompt = payload.get("request")
                if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 4000:
                    return json_response(self, 400, {"error": "request_invalid"})
                plan = plan_with_openai(prompt.strip())
                task_id, now = uuid.uuid4().hex, int(time.time())
                status = "pending_approval" if plan["actions"] else "needs_clarification"
                with connect_db() as db:
                    db.execute("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        (task_id, prompt.strip(), plan["summary"],
                         json.dumps(plan["actions"], ensure_ascii=False), status, now, now, None))
                return json_response(self, 201, {"id": task_id, "request": prompt.strip(),
                    **plan, "status": status, "created_at": now, "updated_at": now})
            parts = self.path.strip("/").split("/")
            if len(parts) == 3 and parts[0] == "tasks" and parts[2] in ("approve", "reject"):
                task_id, operation = parts[1], parts[2]
                payload = self.read_json() if int(self.headers.get("Content-Length", "0")) else {}
                try:
                    index = int(payload.get("action_index", -1))
                except (TypeError, ValueError):
                    return json_response(self, 400, {"error": "action_index_invalid"})
                with LOCK, connect_db() as db:
                    row = db.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
                    if row is None:
                        return json_response(self, 404, {"error": "not_found"})
                    actions = json.loads(row["actions_json"])
                    if index < 0 or index >= len(actions):
                        return json_response(self, 400, {"error": "action_index_invalid"})
                    action = actions[index]
                    if action["status"] != "pending_approval":
                        return json_response(self, 409, {"error": "action_not_pending"})
                    now = int(time.time())
                    if operation == "reject":
                        action["status"] = "rejected"
                    else:
                        action["status"] = "approved"
                        try:
                            action["result"] = run_approved_action(action["action"])
                            action["status"] = "completed" if action["result"]["exit_code"] == 0 else "failed"
                        except Exception as error:
                            action["status"] = "failed"
                            action["result"] = {"error": str(error)[:500]}
                    overall = ("pending_approval" if any(a["status"] == "pending_approval" for a in actions)
                               else "completed" if any(a["status"] == "completed" for a in actions)
                               else "rejected")
                    db.execute("UPDATE tasks SET actions_json=?, status=?, updated_at=? WHERE id=?",
                        (json.dumps(actions, ensure_ascii=False), overall, now, task_id))
                    updated = db.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
                return json_response(self, 200, self.task_dict(updated, True))
        except (ValueError, json.JSONDecodeError) as error:
            return json_response(self, 400, {"error": str(error)[:300]})
        except Exception as error:
            return json_response(self, 502, {"error": str(error)[:500]})
        return json_response(self, 404, {"error": "not_found"})

def main():
    if not ACCESS_TOKEN:
        raise SystemExit("CLOUD_AGENT_TOKEN yapılandırılmamış.")
    connect_db().close()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    print(f"Cloud Agent listening on {HOST}:{PORT}; approval is mandatory.", flush=True)
    server.serve_forever()

if __name__ == "__main__":
    main()
