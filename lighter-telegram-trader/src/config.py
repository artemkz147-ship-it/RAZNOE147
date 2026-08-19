from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


LIVE_ACK_PHRASE = "YES_I_ACCEPT_REAL_MONEY_RISK"


@dataclass(frozen=True)
class Settings:
    telegram_bot_token: str
    telegram_allowed_user_id: int
    trading_mode: str
    network: str
    symbols: tuple[str, ...]
    paper_balance_usdc: float
    risk_per_trade_pct: float
    max_risk_usdc: float
    max_daily_loss_usdc: float
    max_position_notional_usdc: float
    max_leverage: float
    max_open_positions: int
    poll_seconds: int
    max_slippage: float
    lighter_account_index: int | None
    lighter_api_key_index: int
    lighter_api_private_key: str | None
    live_ack: str | None
    database_path: Path

    @property
    def api_url(self) -> str:
        if self.network == "mainnet":
            return "https://mainnet.zklighter.elliot.ai"
        if self.network == "testnet":
            return "https://testnet.zklighter.elliot.ai"
        raise ValueError(f"Unsupported LIGHTER_NETWORK={self.network!r}")

    @property
    def chain_id(self) -> int:
        return 304 if self.network == "mainnet" else 300

    @property
    def live_enabled(self) -> bool:
        return self.trading_mode == "live"

    def validate(self) -> None:
        if not self.telegram_bot_token:
            raise ValueError("TELEGRAM_BOT_TOKEN is required")
        if self.telegram_allowed_user_id <= 0:
            raise ValueError("TELEGRAM_ALLOWED_USER_ID must be a positive integer")
        if self.trading_mode not in {"paper", "live"}:
            raise ValueError("TRADING_MODE must be 'paper' or 'live'")
        if self.network not in {"mainnet", "testnet"}:
            raise ValueError("LIGHTER_NETWORK must be 'mainnet' or 'testnet'")
        if not self.symbols:
            raise ValueError("At least one symbol is required")
        if self.paper_balance_usdc <= 0:
            raise ValueError("PAPER_BALANCE_USDC must be > 0")
        if not (0 < self.risk_per_trade_pct <= 2.0):
            raise ValueError("RISK_PER_TRADE_PCT must be in (0, 2]")
        if self.max_risk_usdc <= 0:
            raise ValueError("MAX_RISK_USDC must be > 0")
        if self.max_daily_loss_usdc <= 0:
            raise ValueError("MAX_DAILY_LOSS_USDC must be > 0")
        if self.max_position_notional_usdc <= 0:
            raise ValueError("MAX_POSITION_NOTIONAL_USDC must be > 0")
        if not (1.0 <= self.max_leverage <= 2.0):
            raise ValueError("MAX_LEVERAGE is deliberately capped at 2.0 in v1")
        if self.max_open_positions != 1:
            raise ValueError("MAX_OPEN_POSITIONS is deliberately fixed to 1 in v1")
        if not (15 <= self.poll_seconds <= 3600):
            raise ValueError("POLL_SECONDS must be between 15 and 3600")
        if not (0 < self.max_slippage <= 0.01):
            raise ValueError("MAX_SLIPPAGE must be in (0, 0.01]")

        if self.live_enabled:
            if self.live_ack != LIVE_ACK_PHRASE:
                raise ValueError(
                    "Live mode is locked. Set LIVE_TRADING_ACK=" + LIVE_ACK_PHRASE
                )
            if self.lighter_account_index is None:
                raise ValueError("LIGHTER_ACCOUNT_INDEX is required in live mode")
            if not self.lighter_api_private_key:
                raise ValueError("LIGHTER_API_PRIVATE_KEY is required in live mode")


def _get_int(name: str, default: int | None = None) -> int | None:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return int(raw)


def load_settings() -> Settings:
    load_dotenv()

    symbols = tuple(
        part.strip().upper()
        for part in os.getenv("SYMBOLS", "BTC").split(",")
        if part.strip()
    )

    settings = Settings(
        telegram_bot_token=os.getenv("TELEGRAM_BOT_TOKEN", "").strip(),
        telegram_allowed_user_id=int(os.getenv("TELEGRAM_ALLOWED_USER_ID", "0")),
        trading_mode=os.getenv("TRADING_MODE", "paper").strip().lower(),
        network=os.getenv("LIGHTER_NETWORK", "mainnet").strip().lower(),
        symbols=symbols,
        paper_balance_usdc=float(os.getenv("PAPER_BALANCE_USDC", "15")),
        risk_per_trade_pct=float(os.getenv("RISK_PER_TRADE_PCT", "0.5")),
        max_risk_usdc=float(os.getenv("MAX_RISK_USDC", "0.12")),
        max_daily_loss_usdc=float(os.getenv("MAX_DAILY_LOSS_USDC", "0.45")),
        max_position_notional_usdc=float(os.getenv("MAX_POSITION_NOTIONAL_USDC", "5")),
        max_leverage=float(os.getenv("MAX_LEVERAGE", "2")),
        max_open_positions=int(os.getenv("MAX_OPEN_POSITIONS", "1")),
        poll_seconds=int(os.getenv("POLL_SECONDS", "60")),
        max_slippage=float(os.getenv("MAX_SLIPPAGE", "0.003")),
        lighter_account_index=_get_int("LIGHTER_ACCOUNT_INDEX"),
        lighter_api_key_index=int(os.getenv("LIGHTER_API_KEY_INDEX", "0")),
        lighter_api_private_key=os.getenv("LIGHTER_API_PRIVATE_KEY") or None,
        live_ack=os.getenv("LIVE_TRADING_ACK") or None,
        database_path=Path(os.getenv("DATABASE_PATH", "trader.sqlite3")),
    )
    settings.validate()
    return settings
