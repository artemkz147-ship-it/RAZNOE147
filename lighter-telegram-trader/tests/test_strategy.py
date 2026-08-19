import math

from src.models import CandleBar, Side
from src.strategy import build_signal


def _bars(n: int, *, start: float, drift: float, amp: float) -> list[CandleBar]:
    out: list[CandleBar] = []
    prev = start
    for i in range(n):
        close = start + drift * i + amp * math.sin(i * 1.7)
        volume = 150.0 if i == n - 1 else 100.0
        out.append(
            CandleBar(
                timestamp=i,
                open=prev,
                high=max(prev, close) + 0.3,
                low=min(prev, close) - 0.3,
                close=close,
                volume=volume,
            )
        )
        prev = close
    return out


def test_long_signal_needs_multifactor_confirmation():
    signal = build_signal(
        "BTC",
        0,
        _bars(120, start=100, drift=0.04, amp=0.5),
        _bars(100, start=100, drift=0.10, amp=0.3),
    )
    assert signal is not None
    assert signal.side == Side.LONG
    assert signal.stop < signal.entry < signal.take


def test_no_signal_without_volume_confirmation():
    bars = _bars(120, start=100, drift=0.04, amp=0.5)
    bars[-1] = CandleBar(
        timestamp=bars[-1].timestamp,
        open=bars[-1].open,
        high=bars[-1].high,
        low=bars[-1].low,
        close=bars[-1].close,
        volume=50.0,
    )
    signal = build_signal(
        "BTC",
        0,
        bars,
        _bars(100, start=100, drift=0.10, amp=0.3),
    )
    assert signal is None
