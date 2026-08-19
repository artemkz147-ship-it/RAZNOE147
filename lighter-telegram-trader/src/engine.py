from __future__ import annotations

import asyncio
import time
from collections.abc import Awaitable, Callable
from datetime import datetime

from .config import Settings
from .gateway import LighterGateway, MarketInfo
from .models import ManagedPosition, Side
from .risk import decide_size
from .storage import Storage
from .strategy import build_signal


Notify = Callable[[str], Awaitable[None]]


class TradingEngine:
    def __init__(
        self,
        settings: Settings,
        gateway: LighterGateway,
        storage: Storage,
        notify: Notify,
    ):
        self.settings = settings
        self.gateway = gateway
        self.storage = storage
        self.notify = notify
        self._tick_lock = asyncio.Lock()
        self._markets: dict[str, MarketInfo] = {}
        self.position: ManagedPosition | None = storage.load_position()
        self.startup_note = ""

    @property
    def enabled(self) -> bool:
        return self.storage.get_bool("enabled", False)

    @property
    def halted(self) -> bool:
        return self.storage.get_bool("halted", False)

    async def startup(self) -> None:
        await self.gateway.open()
        for symbol in self.settings.symbols:
            self._markets[symbol] = await self.gateway.resolve_market(symbol)

        if self.settings.live_enabled:
            live = await self.gateway.live_positions()
            if len(live) > 1:
                self._halt("More than one live position exists on the account")
            elif live:
                only = live[0]
                if (
                    self.position is None
                    or not self.position.live
                    or self.position.market_id != only.market_id
                ):
                    self._halt(
                        "An existing live position was found but it is not the bot's tracked position. "
                        "Bot will not trade until it is resolved."
                    )
            elif self.position is not None:
                self.storage.event("reconcile", action="clear_stale_position")
                self.position = None
                self.storage.save_position(None)
        else:
            # The official PaperClient keeps state in memory. A process restart creates
            # a fresh virtual account, so a persisted paper position cannot be trusted.
            if self.position is not None and not self.position.live:
                self.storage.event("paper_reset", reason="process_restart")
                self.position = None
                self.storage.save_position(None)

        await self._roll_risk_day(force_if_missing=True)

    async def shutdown(self) -> None:
        await self.gateway.close()

    async def set_enabled(self, value: bool) -> str:
        if value and self.halted:
            return "⛔ Торговля заблокирована аварийным стопом. Сначала сбрось блокировку."
        self.storage.set_bool("enabled", value)
        self.storage.event("engine", enabled=value)
        return "▶️ Автоторговля включена" if value else "⏸ Автоторговля остановлена"

    async def reset_halt(self) -> str:
        if self.settings.live_enabled:
            live = await self.gateway.live_positions()
            if len(live) > 1:
                return "Нельзя сбросить блокировку: на счёте больше одной позиции."
            if live and self.position is None:
                return "Нельзя сбросить блокировку: обнаружена чужая/неучтённая позиция."
        self.storage.set_bool("halted", False)
        self.storage.event("halt_reset")
        await self._roll_risk_day(force_if_missing=True)
        return "✅ Аварийная блокировка сброшена. Автоторговля остаётся выключенной."

    async def tick(self) -> None:
        if self._tick_lock.locked():
            return
        async with self._tick_lock:
            try:
                await self._roll_risk_day()
                await self._reconcile_or_manage_position()
                await self._enforce_daily_drawdown()
                if not self.enabled or self.halted or self.position is not None:
                    return
                if self._in_cooldown():
                    return
                await self._scan_for_entry()
            except Exception as exc:
                self.storage.event("tick_error", error=repr(exc))
                self._halt(f"Runtime error: {exc}")
                await self.notify(
                    "🚨 Бот аварийно остановил новые сделки.\n"
                    f"Ошибка: {exc}\n"
                    "Открытая live-позиция, если она есть, остаётся под биржевым SL/TP."
                )

    async def _scan_for_entry(self) -> None:
        for symbol in self.settings.symbols:
            market = self._markets[symbol]
            candles_15m, candles_1h = await asyncio.gather(
                self.gateway.candles(market.market_id, "15m", 120),
                self.gateway.candles(market.market_id, "1h", 100),
            )
            signal = build_signal(symbol, market.market_id, candles_15m, candles_1h)
            if signal is None:
                continue

            balance = await self.gateway.balance_usdc()
            size = decide_size(
                signal,
                balance_usdc=balance,
                risk_per_trade_pct=self.settings.risk_per_trade_pct,
                max_risk_usdc=self.settings.max_risk_usdc,
                max_position_notional_usdc=self.settings.max_position_notional_usdc,
                max_leverage=self.settings.max_leverage,
                size_decimals=market.size_decimals,
                min_base_amount=market.min_base_amount,
                min_quote_amount=market.min_quote_amount,
            )
            if size.base_amount <= 0:
                self.storage.event(
                    "signal_skipped",
                    symbol=symbol,
                    reason=size.reason,
                    notional=size.notional_usdc,
                    risk=size.risk_usdc,
                )
                return

            if self.settings.live_enabled:
                base_amount, entry = await self.gateway.live_open_with_protection(
                    signal, size.base_amount, market
                )
            else:
                base_amount, entry = await self.gateway.paper_open(signal, size.base_amount)

            # Preserve the strategy's absolute stop/take distances around the actual fill.
            stop_distance = abs(signal.entry - signal.stop)
            take_distance = abs(signal.take - signal.entry)
            if signal.side == Side.LONG:
                stop = entry - stop_distance
                take = entry + take_distance
            else:
                stop = entry + stop_distance
                take = entry - take_distance

            self.position = ManagedPosition(
                symbol=symbol,
                market_id=market.market_id,
                side=signal.side,
                base_amount=base_amount,
                entry=entry,
                stop=stop,
                take=take,
                opened_at=int(time.time()),
                live=self.settings.live_enabled,
            )
            self.storage.save_position(self.position)
            self.storage.set("last_trade_ts", str(int(time.time())))
            self.storage.event(
                "opened",
                symbol=symbol,
                side=signal.side.value,
                qty=base_amount,
                entry=entry,
                stop=stop,
                take=take,
                risk=size.risk_usdc,
                live=self.settings.live_enabled,
            )
            await self.notify(
                f"🟢 Открыта {'LIVE' if self.settings.live_enabled else 'PAPER'} сделка\n"
                f"{symbol} {signal.side.value}\n"
                f"Вход: {entry:.4f}\n"
                f"Стоп: {stop:.4f}\n"
                f"Тейк: {take:.4f}\n"
                f"Размер: {base_amount:g} {symbol}\n"
                f"Риск по стопу: ≈ {size.risk_usdc:.3f} USDC"
            )
            return

    async def _reconcile_or_manage_position(self) -> None:
        if self.settings.live_enabled:
            live = await self.gateway.live_positions()
            if self.position is None:
                if live and not self.halted:
                    self._halt("Untracked live position appeared while the bot was running")
                    await self.notify(
                        "🚨 Обнаружена LIVE-позиция, которую бот не открывал. "
                        "Новые сделки заблокированы."
                    )
                return
            if len(live) > 1 or any(p.market_id != self.position.market_id for p in live):
                if not self.halted:
                    self._halt("Live account state conflicts with the tracked bot position")
                    await self.notify(
                        "🚨 Состояние LIVE-счёта не совпадает с позицией бота. "
                        "Новые сделки заблокированы."
                    )
                return
            match = next((p for p in live if p.market_id == self.position.market_id), None)
            if match is None:
                old = self.position
                self.position = None
                self.storage.save_position(None)
                self.storage.set("last_trade_ts", str(int(time.time())))
                self.storage.event("live_position_closed", symbol=old.symbol)
                await self.notify(
                    f"✅ LIVE позиция {old.symbol} закрыта на стороне Lighter. "
                    "Новые входы будут разрешены после cooldown."
                )
            return

        if self.position is None:
            return

        # Paper mode: emulate exchange-side SL/TP using candle extremes.
        candles = await self.gateway.candles(self.position.market_id, "15m", 3)
        if not candles:
            return
        last = candles[-1]
        hit_stop = (
            last.low <= self.position.stop
            if self.position.side == Side.LONG
            else last.high >= self.position.stop
        )
        hit_take = (
            last.high >= self.position.take
            if self.position.side == Side.LONG
            else last.low <= self.position.take
        )
        # If both levels were crossed inside one candle, assume the stop happened first.
        if hit_stop and hit_take:
            hit_take = False
        if not (hit_stop or hit_take):
            return

        old = self.position
        _, exit_price = await self.gateway.paper_close(old)
        pnl = (
            (exit_price - old.entry) * old.base_amount
            if old.side == Side.LONG
            else (old.entry - exit_price) * old.base_amount
        )
        self.storage.add_closed_trade(old.symbol, old.side, pnl, paper=True)
        self.storage.event(
            "paper_closed",
            symbol=old.symbol,
            exit=exit_price,
            pnl=pnl,
            reason="stop" if hit_stop else "take",
        )
        self.position = None
        self.storage.save_position(None)
        self.storage.set("last_trade_ts", str(int(time.time())))
        await self.notify(
            f"{'🛑' if hit_stop else '🎯'} PAPER {old.symbol} закрыт\n"
            f"Выход: {exit_price:.4f}\n"
            f"P&L: {pnl:+.4f} USDC"
        )

    async def emergency_close(self) -> str:
        self.storage.set_bool("enabled", False)
        if self.position is None:
            return "Открытой позиции, которую ведёт бот, нет. Автоторговля выключена."
        old = self.position
        market = self._markets[old.symbol]
        if self.settings.live_enabled:
            live = await self.gateway.live_positions()
            match = next((p for p in live if p.market_id == old.market_id), None)
            if match is not None:
                await self.gateway.live_close_market(
                    market=market,
                    side=old.side,
                    base_amount=match.size,
                )
            self.storage.event("emergency_close_sent", symbol=old.symbol)
            return "🚨 Reduce-only market-закрытие отправлено. Бот проверит позицию на следующем цикле."

        _, exit_price = await self.gateway.paper_close(old)
        pnl = (
            (exit_price - old.entry) * old.base_amount
            if old.side == Side.LONG
            else (old.entry - exit_price) * old.base_amount
        )
        self.storage.add_closed_trade(old.symbol, old.side, pnl, paper=True)
        self.position = None
        self.storage.save_position(None)
        self.storage.set("last_trade_ts", str(int(time.time())))
        return f"PAPER позиция закрыта. P&L: {pnl:+.4f} USDC"

    async def status_text(self) -> str:
        value = await self.gateway.account_value_usdc()
        balance = await self.gateway.balance_usdc()
        drawdown = await self._current_daily_drawdown(value)
        mode = "🔴 LIVE" if self.settings.live_enabled else "🟡 PAPER"
        pos = "нет"
        if self.position:
            pos = (
                f"{self.position.symbol} {self.position.side.value}, "
                f"qty={self.position.base_amount:g}, entry={self.position.entry:.4f}, "
                f"SL={self.position.stop:.4f}, TP={self.position.take:.4f}"
            )
        return (
            f"{mode}\n"
            f"Автоторговля: {'ВКЛ' if self.enabled else 'ВЫКЛ'}\n"
            f"Аварийный стоп: {'ДА' if self.halted else 'нет'}\n"
            f"Доступный баланс: {balance:.4f} USDC\n"
            f"Стоимость счёта: {value:.4f} USDC\n"
            f"Просадка за день: {drawdown:.4f} / {self.settings.max_daily_loss_usdc:.4f} USDC\n"
            f"Позиция: {pos}"
        )

    async def _enforce_daily_drawdown(self) -> None:
        value = await self.gateway.account_value_usdc()
        drawdown = await self._current_daily_drawdown(value)
        if drawdown >= self.settings.max_daily_loss_usdc:
            if not self.halted:
                self._halt(f"Daily account-value drawdown reached {drawdown:.4f} USDC")
                await self.notify(
                    "🧯 Достигнут дневной лимит потерь. Новые сделки заблокированы.\n"
                    f"Просадка: {drawdown:.4f} USDC"
                )

    async def _roll_risk_day(self, force_if_missing: bool = False) -> None:
        day = datetime.now().astimezone().date().isoformat()
        stored_day = self.storage.get("risk_day")
        if stored_day != day or (
            force_if_missing and self.storage.get("risk_day_start_value") is None
        ):
            value = await self.gateway.account_value_usdc()
            self.storage.set("risk_day", day)
            self.storage.set("risk_day_start_value", repr(value))
            self.storage.event("risk_day_start", day=day, value=value)

    async def _current_daily_drawdown(self, current_value: float | None = None) -> float:
        if current_value is None:
            current_value = await self.gateway.account_value_usdc()
        raw = self.storage.get("risk_day_start_value")
        if raw is None:
            return 0.0
        start = float(raw)
        return max(0.0, start - current_value)

    def _in_cooldown(self) -> bool:
        raw = self.storage.get("last_trade_ts")
        if raw is None:
            return False
        return time.time() - int(raw) < 30 * 60

    def _halt(self, reason: str) -> None:
        self.storage.set_bool("halted", True)
        self.storage.set_bool("enabled", False)
        self.storage.event("halt", reason=reason)
        self.startup_note = reason
