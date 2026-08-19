from __future__ import annotations

import json
import sqlite3
import time
from dataclasses import asdict
from pathlib import Path

from .models import ManagedPosition, Side


class Storage:
    def __init__(self, path: Path):
        self.path = path
        self.conn = sqlite3.connect(path)
        self.conn.row_factory = sqlite3.Row
        self._init()

    def _init(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS state (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                kind TEXT NOT NULL,
                payload TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS closed_trades (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                closed_at INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                side TEXT NOT NULL,
                pnl_usdc REAL NOT NULL,
                paper INTEGER NOT NULL
            );
            """
        )
        self.conn.commit()

    def set(self, key: str, value: str) -> None:
        self.conn.execute(
            "INSERT INTO state(key,value) VALUES(?,?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            (key, value),
        )
        self.conn.commit()

    def get(self, key: str, default: str | None = None) -> str | None:
        row = self.conn.execute("SELECT value FROM state WHERE key=?", (key,)).fetchone()
        return row["value"] if row else default

    def set_bool(self, key: str, value: bool) -> None:
        self.set(key, "1" if value else "0")

    def get_bool(self, key: str, default: bool = False) -> bool:
        raw = self.get(key)
        return default if raw is None else raw == "1"

    def save_position(self, position: ManagedPosition | None) -> None:
        if position is None:
            self.set("position", "")
            return
        payload = asdict(position)
        payload["side"] = position.side.value
        self.set("position", json.dumps(payload, separators=(",", ":")))

    def load_position(self) -> ManagedPosition | None:
        raw = self.get("position", "")
        if not raw:
            return None
        data = json.loads(raw)
        data["side"] = Side(data["side"])
        return ManagedPosition(**data)

    def event(self, kind: str, **payload: object) -> None:
        self.conn.execute(
            "INSERT INTO events(ts,kind,payload) VALUES(?,?,?)",
            (int(time.time()), kind, json.dumps(payload, ensure_ascii=False)),
        )
        self.conn.commit()

    def add_closed_trade(self, symbol: str, side: Side, pnl_usdc: float, paper: bool) -> None:
        self.conn.execute(
            "INSERT INTO closed_trades(closed_at,symbol,side,pnl_usdc,paper) VALUES(?,?,?,?,?)",
            (int(time.time()), symbol, side.value, float(pnl_usdc), 1 if paper else 0),
        )
        self.conn.commit()

    def daily_realized_pnl(self, *, paper: bool) -> float:
        now = time.localtime()
        midnight = int(
            time.mktime((now.tm_year, now.tm_mon, now.tm_mday, 0, 0, 0, now.tm_wday, now.tm_yday, now.tm_isdst))
        )
        row = self.conn.execute(
            "SELECT COALESCE(SUM(pnl_usdc),0) AS pnl FROM closed_trades "
            "WHERE closed_at>=? AND paper=?",
            (midnight, 1 if paper else 0),
        ).fetchone()
        return float(row["pnl"])

    def recent_events(self, limit: int = 10) -> list[sqlite3.Row]:
        return list(
            self.conn.execute(
                "SELECT ts,kind,payload FROM events ORDER BY id DESC LIMIT ?", (limit,)
            )
        )
