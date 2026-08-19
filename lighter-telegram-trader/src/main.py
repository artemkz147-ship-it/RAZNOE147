from __future__ import annotations

import logging

from telegram import Update

from .config import load_settings
from .engine import TradingEngine
from .gateway import LighterGateway
from .storage import Storage
from .telegram_app import TelegramRuntime


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    settings = load_settings()
    storage = Storage(settings.database_path)
    gateway = LighterGateway(settings)

    async def bootstrap_notify(_: str) -> None:
        return None

    engine = TradingEngine(settings, gateway, storage, bootstrap_notify)
    runtime = TelegramRuntime(settings, engine)
    app = runtime.build()
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()
