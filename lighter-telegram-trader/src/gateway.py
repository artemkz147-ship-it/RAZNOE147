from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass

import lighter
from lighter.signer_client import CreateOrderTxReq

from .config import Settings
from .models import CandleBar, ManagedPosition, Side, TradeSignal


@dataclass(frozen=True)
class MarketInfo:
    symbol: str
    market_id: int
    size_decimals: int
    price_decimals: int
    min_base_amount: float
    min_quote_amount: float


@dataclass(frozen=True)
class LivePositionSnapshot:
    market_id: int
    symbol: str
    size: float
    avg_entry_price: float
    unrealized_pnl: float
    sign: int


class LighterGateway:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.api_client: lighter.ApiClient | None = None
        self.order_api: lighter.OrderApi | None = None
        self.candle_api: lighter.CandlestickApi | None = None
        self.account_api: lighter.AccountApi | None = None
        self.paper: lighter.PaperClient | None = None
        self.signer: lighter.SignerClient | None = None
        self._markets: dict[str, MarketInfo] = {}

    async def open(self) -> None:
        self.api_client = lighter.ApiClient(
            configuration=lighter.Configuration(host=self.settings.api_url)
        )
        self.order_api = lighter.OrderApi(self.api_client)
        self.candle_api = lighter.CandlestickApi(self.api_client)
        self.account_api = lighter.AccountApi(self.api_client)

        if self.settings.live_enabled:
            assert self.settings.lighter_account_index is not None
            assert self.settings.lighter_api_private_key is not None
            self.signer = lighter.SignerClient(
                url=self.settings.api_url,
                account_index=self.settings.lighter_account_index,
                api_private_keys={
                    self.settings.lighter_api_key_index: self.settings.lighter_api_private_key
                },
                chain_id=self.settings.chain_id,
            )
            err = self.signer.check_client()
            if err is not None:
                raise RuntimeError(f"Lighter API key preflight failed: {err}")
        else:
            self.paper = lighter.PaperClient(
                self.api_client,
                initial_collateral_usdc=self.settings.paper_balance_usdc,
            )

    async def close(self) -> None:
        if self.paper is not None:
            await self.paper.close()
        if self.signer is not None:
            await self.signer.close()
        if self.api_client is not None:
            await self.api_client.close()

    async def resolve_market(self, symbol: str) -> MarketInfo:
        symbol = symbol.upper()
        cached = self._markets.get(symbol)
        if cached:
            return cached
        assert self.order_api is not None
        books = await self.order_api.order_books()
        for item in books.order_books:
            if (
                item.symbol.upper() == symbol
                and item.market_type == "perp"
                and item.status == "active"
            ):
                info = MarketInfo(
                    symbol=item.symbol.upper(),
                    market_id=item.market_id,
                    size_decimals=item.supported_size_decimals,
                    price_decimals=item.supported_price_decimals,
                    min_base_amount=float(item.min_base_amount),
                    min_quote_amount=float(item.min_quote_amount),
                )
                self._markets[symbol] = info
                return info
        raise RuntimeError(f"Active perpetual market {symbol!r} not found")

    async def candles(self, market_id: int, resolution: str, count: int) -> list[CandleBar]:
        assert self.candle_api is not None
        now = int(time.time())
        response = await self.candle_api.candles(
            market_id=market_id,
            resolution=resolution,
            start_timestamp=now - 60 * 60 * 24 * 120,
            end_timestamp=now,
            count_back=min(count, 500),
        )
        return [
            CandleBar(
                timestamp=int(c.t),
                open=float(c.o),
                high=float(c.h),
                low=float(c.l),
                close=float(c.c),
                volume=float(c.v),
            )
            for c in response.c
        ]

    async def balance_usdc(self) -> float:
        if self.paper is not None:
            return float(self.paper.get_portfolio_value())
        account = await self._live_account()
        return float(account.available_balance)

    async def account_value_usdc(self) -> float:
        if self.paper is not None:
            return float(self.paper.get_portfolio_value())
        account = await self._live_account()
        return float(account.total_asset_value)

    async def live_positions(self) -> list[LivePositionSnapshot]:
        if not self.settings.live_enabled:
            return []
        account = await self._live_account()
        result: list[LivePositionSnapshot] = []
        for p in account.positions:
            size = float(p.position)
            if size <= 0:
                continue
            result.append(
                LivePositionSnapshot(
                    market_id=p.market_id,
                    symbol=p.symbol.upper(),
                    size=size,
                    avg_entry_price=float(p.avg_entry_price),
                    unrealized_pnl=float(p.unrealized_pnl),
                    sign=p.sign,
                )
            )
        return result

    async def _live_account(self):
        if not self.settings.live_enabled:
            raise RuntimeError("Live account requested in paper mode")
        assert self.account_api is not None
        assert self.settings.lighter_account_index is not None
        response = await self.account_api.account(
            by="index",
            value=str(self.settings.lighter_account_index),
        )
        if not response.accounts:
            raise RuntimeError("Lighter account not found")
        return response.accounts[0]

    async def paper_open(self, signal: TradeSignal, base_amount: float) -> tuple[float, float]:
        if self.paper is None:
            raise RuntimeError("paper_open called outside paper mode")
        await self.paper.track_market_snapshot(signal.market_id)
        side = (
            lighter.PaperOrderSide.BUY
            if signal.side == Side.LONG
            else lighter.PaperOrderSide.SELL
        )
        result = await self.paper.create_paper_order(
            lighter.PaperOrderRequest(
                market_id=signal.market_id,
                side=side,
                base_amount=base_amount,
            )
        )
        if result.filled_size <= 0:
            raise RuntimeError("Paper order was not filled")
        return float(result.filled_size), float(result.avg_price)

    async def paper_close(self, position: ManagedPosition) -> tuple[float, float]:
        if self.paper is None:
            raise RuntimeError("paper_close called outside paper mode")
        await self.paper.refresh_order_book(position.market_id)
        side = (
            lighter.PaperOrderSide.SELL
            if position.side == Side.LONG
            else lighter.PaperOrderSide.BUY
        )
        result = await self.paper.create_paper_order(
            lighter.PaperOrderRequest(
                market_id=position.market_id,
                side=side,
                base_amount=position.base_amount,
            )
        )
        if result.filled_size <= 0:
            raise RuntimeError("Paper close was not filled")
        return float(result.filled_size), float(result.avg_price)

    async def live_open_with_protection(
        self,
        signal: TradeSignal,
        base_amount: float,
        market: MarketInfo,
    ) -> tuple[float, float]:
        if self.signer is None:
            raise RuntimeError("live_open called outside live mode")

        amount_int = self._base_to_int(base_amount, market.size_decimals)
        if amount_int <= 0:
            raise RuntimeError("Order amount rounded to zero")

        entry_id = self._client_order_id()
        is_ask = signal.side == Side.SHORT
        _, response, err = await self.signer.create_market_order_if_slippage(
            market_index=market.market_id,
            client_order_index=entry_id,
            base_amount=amount_int,
            max_slippage=self.settings.max_slippage,
            is_ask=is_ask,
            reduce_only=False,
        )
        self._raise_if_tx_failed(response, err, "entry")

        # Read the actual position after the fill. If the API has not updated yet,
        # fall back to the requested size/strategy price only for emergency handling.
        actual_size = base_amount
        actual_entry = signal.entry
        for attempt in range(4):
            positions = await self.live_positions()
            match = next((p for p in positions if p.market_id == market.market_id), None)
            if match is not None and match.size > 0:
                actual_size = match.size
                if match.avg_entry_price > 0:
                    actual_entry = match.avg_entry_price
                break
            if attempt < 3:
                await asyncio.sleep(0.25)

        stop_distance = abs(signal.entry - signal.stop)
        take_distance = abs(signal.take - signal.entry)
        protected_signal = TradeSignal(
            symbol=signal.symbol,
            market_id=signal.market_id,
            side=signal.side,
            entry=actual_entry,
            stop=(actual_entry - stop_distance if signal.side == Side.LONG else actual_entry + stop_distance),
            take=(actual_entry + take_distance if signal.side == Side.LONG else actual_entry - take_distance),
            atr=signal.atr,
            rsi=signal.rsi,
            reason=signal.reason,
        )

        try:
            await self._place_position_tied_oco(protected_signal, market)
        except Exception:
            # A live position without a stop is forbidden. Best-effort emergency close.
            await self.live_close_market(
                market=market,
                side=signal.side,
                base_amount=actual_size,
            )
            raise

        return actual_size, actual_entry

    async def _place_position_tied_oco(
        self,
        signal: TradeSignal,
        market: MarketInfo,
    ) -> None:
        assert self.signer is not None
        closing_is_ask = signal.side == Side.LONG
        # Limit price is deliberately made a little more aggressive than the trigger
        # to improve fill probability after the trigger fires.
        if closing_is_ask:
            tp_limit = signal.take * 0.997
            sl_limit = signal.stop * 0.995
        else:
            tp_limit = signal.take * 1.003
            sl_limit = signal.stop * 1.005

        tp = CreateOrderTxReq(
            MarketIndex=market.market_id,
            ClientOrderIndex=0,
            BaseAmount=0,
            Price=self._price_to_int(tp_limit, market.price_decimals),
            IsAsk=1 if closing_is_ask else 0,
            Type=self.signer.ORDER_TYPE_TAKE_PROFIT_LIMIT,
            TimeInForce=self.signer.ORDER_TIME_IN_FORCE_GOOD_TILL_TIME,
            ReduceOnly=1,
            TriggerPrice=self._price_to_int(signal.take, market.price_decimals),
            OrderExpiry=-1,
        )
        sl = CreateOrderTxReq(
            MarketIndex=market.market_id,
            ClientOrderIndex=0,
            BaseAmount=0,
            Price=self._price_to_int(sl_limit, market.price_decimals),
            IsAsk=1 if closing_is_ask else 0,
            Type=self.signer.ORDER_TYPE_STOP_LOSS_LIMIT,
            TimeInForce=self.signer.ORDER_TIME_IN_FORCE_GOOD_TILL_TIME,
            ReduceOnly=1,
            TriggerPrice=self._price_to_int(signal.stop, market.price_decimals),
            OrderExpiry=-1,
        )
        _, response, err = await self.signer.create_grouped_orders(
            grouping_type=self.signer.GROUPING_TYPE_ONE_CANCELS_THE_OTHER,
            orders=[tp, sl],
        )
        self._raise_if_tx_failed(response, err, "protective OCO")

    async def live_close_market(
        self,
        *,
        market: MarketInfo,
        side: Side,
        base_amount: float,
    ) -> None:
        if self.signer is None:
            raise RuntimeError("live_close called outside live mode")
        amount_int = self._base_to_int(base_amount, market.size_decimals)
        if amount_int <= 0:
            return
        # Long is closed by selling; short is closed by buying.
        close_is_ask = side == Side.LONG
        _, response, err = await self.signer.create_market_order_if_slippage(
            market_index=market.market_id,
            client_order_index=self._client_order_id(),
            base_amount=amount_int,
            max_slippage=self.settings.max_slippage,
            is_ask=close_is_ask,
            reduce_only=True,
        )
        self._raise_if_tx_failed(response, err, "emergency close")

    @staticmethod
    def _client_order_id() -> int:
        return int(time.time_ns() // 1_000) & 0x7FFF_FFFF_FFFF_FFFF

    @staticmethod
    def _base_to_int(value: float, decimals: int) -> int:
        return int(round(value * (10**decimals)))

    @staticmethod
    def _price_to_int(value: float, decimals: int) -> int:
        return int(round(value * (10**decimals)))

    @staticmethod
    def _raise_if_tx_failed(response, err: str | None, label: str) -> None:
        if err:
            raise RuntimeError(f"Lighter {label} failed: {err}")
        if response is None:
            raise RuntimeError(f"Lighter {label} returned no response")
        code = getattr(response, "code", None)
        if code is not None and code != 200:
            raise RuntimeError(
                f"Lighter {label} failed: code={code}, message={getattr(response, 'message', '')}"
            )
