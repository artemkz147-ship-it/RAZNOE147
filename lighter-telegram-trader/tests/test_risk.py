from src.models import Side, TradeSignal
from src.risk import decide_size, floor_to_decimals


def _signal() -> TradeSignal:
    return TradeSignal(
        symbol="BTC",
        market_id=0,
        side=Side.LONG,
        entry=100.0,
        stop=98.0,
        take=103.0,
        atr=1.0,
        rsi=58.0,
        reason="test",
    )


def test_floor_to_decimals_never_rounds_up():
    assert floor_to_decimals(1.239, 2) == 1.23


def test_size_is_limited_by_risk_budget():
    d = decide_size(
        _signal(),
        balance_usdc=100,
        risk_per_trade_pct=0.5,
        max_risk_usdc=0.4,
        max_position_notional_usdc=100,
        max_leverage=2,
        size_decimals=3,
        min_base_amount=0.001,
        min_quote_amount=1.0,
    )
    assert d.base_amount == 0.2
    assert d.risk_usdc <= 0.4 + 1e-9


def test_does_not_increase_order_to_exchange_minimum():
    d = decide_size(
        _signal(),
        balance_usdc=10,
        risk_per_trade_pct=0.1,
        max_risk_usdc=0.01,
        max_position_notional_usdc=10,
        max_leverage=2,
        size_decimals=4,
        min_base_amount=1.0,
        min_quote_amount=100.0,
    )
    assert d.base_amount == 0.0
    assert "minimum" in d.reason
