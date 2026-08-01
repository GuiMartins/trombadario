from datetime import UTC, datetime

from sqlalchemy import DateTime, Dialect
from sqlalchemy.types import TypeDecorator


class UtcDateTime(TypeDecorator):
    """Timezone-aware datetime that actually survives SQLite.

    SQLite has no datetime type and stores whatever string it is handed, so
    `DateTime(timezone=True)` silently drops the offset and reads back naive.
    Left alone, the API would serialize `2026-08-01T14:30:00` with no offset and
    the Android client would parse it as local time - a 3-hour error in Brazil.

    Everything is normalized to UTC on the way in and tagged as UTC on the way
    out; rendering in local time is the client's job.
    """

    impl = DateTime(timezone=True)
    cache_ok = True

    def process_bind_param(self, value: datetime | None, dialect: Dialect) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None:
            raise ValueError("datetime sem timezone - use datetime.now(timezone.utc)")
        return value.astimezone(UTC)

    def process_result_value(self, value: datetime | None, dialect: Dialect) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value.astimezone(UTC)
