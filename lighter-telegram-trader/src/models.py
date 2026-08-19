from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Side(str, Enum):
    LONG = "LONG"
    SHORT = "SHORT"


@dataclass(frozen=True)
class CandleBar:
    timestamp: int
    open: float
    high: float
    low: float
    close: float
    volume: float


@dataclass(frozen=True)
class TradeSignal:
    symbol: str
    market_id: int
    side: Side
    entry: float
    stop: float
    take: float
    atr: float
    rsi: float
    reason: str


@dataclass(frozen=True)
class SizeDecision:
    base_amount: float
    notional_usdc: float
    risk_usdc: float
    reason: str


@dataclass
class ManagedPosition:
    symbol: str
    market_id: int
    side: Side
    base_amount: float
    entry: float
    stop: float
    take: float
    opened_at: int
    live: bool
