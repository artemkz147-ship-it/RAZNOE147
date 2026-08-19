from __future__ import annotations

import math

from .models import SizeDecision, TradeSignal


def floor_to_decimals(value: float, decimals: int) -> float:
    factor = 10 ** decimals
    return math.floor(value * factor + 1e-12) / factor


def decide_size(
    signal: TradeSignal,
    *,
    balance_usdc: float,
    risk_per_trade_pct: float,
    max_risk_usdc: float,
    max_position_notional_usdc: float,
    max_leverage: float,
    size_decimals: int,
    min_base_amount: float,
    min_quote_amount: float,
) -> SizeDecision:
    stop_distance = abs(signal.entry - signal.stop)
    if balance_usdc <= 0 or stop_distance <= 0 or signal.entry <= 0:
        return SizeDecision(0.0, 0.0, 0.0, "invalid balance/price/stop")

    risk_budget = min(balance_usdc * (risk_per_trade_pct / 100.0), max_risk_usdc)
    qty_by_risk = risk_budget / stop_distance
    qty_by_notional = max_position_notional_usdc / signal.entry
    qty_by_margin = (balance_usdc * max_leverage) / signal.entry
    qty = min(qty_by_risk, qty_by_notional, qty_by_margin)
    qty = floor_to_decimals(qty, size_decimals)

    notional = qty * signal.entry
    actual_risk = qty * stop_distance

    if qty <= 0:
        return SizeDecision(0.0, 0.0, 0.0, "calculated size rounds to zero")
    if qty < min_base_amount:
        return SizeDecision(
            0.0,
            notional,
            actual_risk,
            "exchange minimum base size would exceed configured risk",
        )
    if notional < min_quote_amount:
        return SizeDecision(
            0.0,
            notional,
            actual_risk,
            "exchange minimum notional would exceed configured risk",
        )

    return SizeDecision(qty, notional, actual_risk, "ok")
