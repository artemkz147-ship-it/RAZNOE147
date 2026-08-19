from pathlib import Path

import pytest

from src.config import LIVE_ACK_PHRASE, Settings


def _settings(**overrides) -> Settings:
    values = dict(
        telegram_bot_token="token",
        telegram_allowed_user_id=123,
        trading_mode="paper",
        network="mainnet",
        symbols=("BTC",),
        paper_balance_usdc=15.0,
        risk_per_trade_pct=0.5,
        max_risk_usdc=0.12,
        max_daily_loss_usdc=0.45,
        max_position_notional_usdc=5.0,
        max_leverage=2.0,
        max_open_positions=1,
        poll_seconds=60,
        max_slippage=0.003,
        lighter_account_index=None,
        lighter_api_key_index=0,
        lighter_api_private_key=None,
        live_ack=None,
        database_path=Path("test.sqlite3"),
    )
    values.update(overrides)
    return Settings(**values)


def test_live_mode_is_locked_without_explicit_ack():
    settings = _settings(
        trading_mode="live",
        lighter_account_index=1,
        lighter_api_private_key="private",
    )
    with pytest.raises(ValueError, match="Live mode is locked"):
        settings.validate()


def test_live_mode_accepts_ack_but_leverage_stays_hard_capped():
    settings = _settings(
        trading_mode="live",
        lighter_account_index=1,
        lighter_api_private_key="private",
        live_ack=LIVE_ACK_PHRASE,
        max_leverage=2.1,
    )
    with pytest.raises(ValueError, match="capped at 2.0"):
        settings.validate()
