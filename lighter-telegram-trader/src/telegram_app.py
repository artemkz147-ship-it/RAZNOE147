from __future__ import annotations

import logging

from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.ext import (
    Application,
    ApplicationBuilder,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
)

from .config import Settings
from .engine import TradingEngine


log = logging.getLogger(__name__)


class TelegramRuntime:
    def __init__(self, settings: Settings, engine: TradingEngine):
        self.settings = settings
        self.engine = engine
        self.app: Application | None = None

    def build(self) -> Application:
        app = (
            ApplicationBuilder()
            .token(self.settings.telegram_bot_token)
            .post_init(self._post_init)
            .post_shutdown(self._post_shutdown)
            .build()
        )
        app.add_handler(CommandHandler("start", self._start))
        app.add_handler(CommandHandler("status", self._status))
        app.add_handler(CallbackQueryHandler(self._callback))
        self.app = app
        self.engine.notify = self.notify
        return app

    async def notify(self, text: str) -> None:
        if self.app is None:
            return
        try:
            await self.app.bot.send_message(
                chat_id=self.settings.telegram_allowed_user_id,
                text=text,
            )
        except Exception:
            log.exception("Failed to send Telegram notification")

    async def _post_init(self, app: Application) -> None:
        await self.engine.startup()
        if app.job_queue is None:
            raise RuntimeError(
                "python-telegram-bot JobQueue is unavailable; install the [job-queue] extra"
            )
        app.job_queue.run_repeating(
            self._tick_job,
            interval=self.settings.poll_seconds,
            first=3,
            name="trading-engine",
        )
        mode = "LIVE" if self.settings.live_enabled else "PAPER"
        await self.notify(
            f"🤖 Lighter Trader запущен в режиме {mode}.\n"
            "Автоторговля по умолчанию выключена. Открой /start и включи её вручную."
        )
        if self.engine.startup_note:
            await self.notify(f"⚠️ Стартовая блокировка: {self.engine.startup_note}")

    async def _post_shutdown(self, app: Application) -> None:
        await self.engine.shutdown()

    async def _tick_job(self, context: ContextTypes.DEFAULT_TYPE) -> None:
        await self.engine.tick()

    def _is_owner(self, update: Update) -> bool:
        user = update.effective_user
        return user is not None and user.id == self.settings.telegram_allowed_user_id

    def _menu(self) -> InlineKeyboardMarkup:
        return InlineKeyboardMarkup(
            [
                [
                    InlineKeyboardButton("▶️ Старт", callback_data="start_trading"),
                    InlineKeyboardButton("⏸ Стоп", callback_data="stop_trading"),
                ],
                [
                    InlineKeyboardButton("📊 Статус", callback_data="status"),
                    InlineKeyboardButton("🚨 Закрыть позицию", callback_data="close"),
                ],
                [
                    InlineKeyboardButton("🧯 Сбросить блокировку", callback_data="reset_halt"),
                ],
            ]
        )

    async def _start(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        if not self._is_owner(update):
            return
        mode = "🔴 LIVE — реальные деньги" if self.settings.live_enabled else "🟡 PAPER — виртуальные деньги"
        text = (
            f"🤖 Lighter Trader\n{mode}\n\n"
            "Старт — разрешает новые автоматические входы.\n"
            "Стоп — запрещает новые входы, но не снимает защиту с уже открытой позиции.\n"
            "Закрыть позицию — аварийный reduce-only выход."
        )
        if update.message:
            await update.message.reply_text(text, reply_markup=self._menu())

    async def _status(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        if not self._is_owner(update):
            return
        text = await self.engine.status_text()
        if update.message:
            await update.message.reply_text(text, reply_markup=self._menu())

    async def _callback(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        if not self._is_owner(update):
            return
        query = update.callback_query
        if query is None:
            return
        await query.answer()

        try:
            if query.data == "start_trading":
                text = await self.engine.set_enabled(True)
            elif query.data == "stop_trading":
                text = await self.engine.set_enabled(False)
            elif query.data == "status":
                text = await self.engine.status_text()
            elif query.data == "close":
                text = await self.engine.emergency_close()
            elif query.data == "reset_halt":
                text = await self.engine.reset_halt()
            else:
                text = "Неизвестная команда"
        except Exception as exc:
            self.engine.storage.event("telegram_action_error", action=query.data, error=repr(exc))
            text = f"⚠️ Ошибка: {exc}"

        await query.edit_message_text(text=text, reply_markup=self._menu())
