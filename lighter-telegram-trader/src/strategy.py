from __future__ import annotations

from statistics import fmean

from .models import CandleBar, Side, TradeSignal


def ema(values: list[float], period: int) -> list[float]:
    if len(values) < period:
        return []
    seed = fmean(values[:period])
    out = [seed]
    alpha = 2.0 / (period + 1.0)
    prev = seed
    for value in values[period:]:
        prev = alpha * value + (1.0 - alpha) * prev
        out.append(prev)
    return out


def rsi(values: list[float], period: int = 14) -> list[float]:
    if len(values) <= period:
        return []
    gains: list[float] = []
    losses: list[float] = []
    for prev, cur in zip(values, values[1:]):
        delta = cur - prev
        gains.append(max(delta, 0.0))
        losses.append(max(-delta, 0.0))

    avg_gain = fmean(gains[:period])
    avg_loss = fmean(losses[:period])
    result: list[float] = []

    def _value(g: float, l: float) -> float:
        if l == 0:
            return 100.0
        rs = g / l
        return 100.0 - (100.0 / (1.0 + rs))

    result.append(_value(avg_gain, avg_loss))
    for gain, loss in zip(gains[period:], losses[period:]):
        avg_gain = ((avg_gain * (period - 1)) + gain) / period
        avg_loss = ((avg_loss * (period - 1)) + loss) / period
        result.append(_value(avg_gain, avg_loss))
    return result


def atr(candles: list[CandleBar], period: int = 14) -> list[float]:
    if len(candles) <= period:
        return []
    trs: list[float] = []
    for prev, cur in zip(candles, candles[1:]):
        tr = max(
            cur.high - cur.low,
            abs(cur.high - prev.close),
            abs(cur.low - prev.close),
        )
        trs.append(tr)
    first = fmean(trs[:period])
    result = [first]
    prev_atr = first
    for tr in trs[period:]:
        prev_atr = ((prev_atr * (period - 1)) + tr) / period
        result.append(prev_atr)
    return result


def build_signal(
    symbol: str,
    market_id: int,
    candles_15m: list[CandleBar],
    candles_1h: list[CandleBar],
) -> TradeSignal | None:
    if len(candles_15m) < 80 or len(candles_1h) < 60:
        return None

    closes = [c.close for c in candles_15m]
    vols = [c.volume for c in candles_15m]
    htf_closes = [c.close for c in candles_1h]

    ema20 = ema(closes, 20)[-1]
    ema50 = ema(closes, 50)[-1]
    htf_ema50 = ema(htf_closes, 50)[-1]
    last_rsi = rsi(closes, 14)[-1]
    last_atr = atr(candles_15m, 14)[-1]
    last = candles_15m[-1]
    volume_avg = fmean(vols[-21:-1])

    if last.close <= 0 or last_atr <= 0 or volume_avg <= 0:
        return None

    atr_pct = last_atr / last.close
    volume_ok = last.volume >= volume_avg * 1.05
    volatility_ok = 0.001 <= atr_pct <= 0.05

    long_ok = (
        last.close > ema20 > ema50
        and candles_1h[-1].close > htf_ema50
        and 52.0 <= last_rsi <= 68.0
        and volume_ok
        and volatility_ok
    )
    short_ok = (
        last.close < ema20 < ema50
        and candles_1h[-1].close < htf_ema50
        and 32.0 <= last_rsi <= 48.0
        and volume_ok
        and volatility_ok
    )

    if long_ok:
        stop = last.close - 1.5 * last_atr
        take = last.close + 2.25 * last_atr
        return TradeSignal(
            symbol=symbol,
            market_id=market_id,
            side=Side.LONG,
            entry=last.close,
            stop=stop,
            take=take,
            atr=last_atr,
            rsi=last_rsi,
            reason="15m uptrend + 1h confirmation + RSI/volume/ATR filter",
        )

    if short_ok:
        stop = last.close + 1.5 * last_atr
        take = last.close - 2.25 * last_atr
        return TradeSignal(
            symbol=symbol,
            market_id=market_id,
            side=Side.SHORT,
            entry=last.close,
            stop=stop,
            take=take,
            atr=last_atr,
            rsi=last_rsi,
            reason="15m downtrend + 1h confirmation + RSI/volume/ATR filter",
        )

    return None
