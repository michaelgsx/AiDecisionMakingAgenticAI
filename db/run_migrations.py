#!/usr/bin/env python3
"""Run V*.sql migrations in this folder against Azure SQL (shared ai-rag-db-1)."""

import os
import re
import sys
from pathlib import Path

from dotenv import load_dotenv

DB_DIR = Path(__file__).resolve().parent
load_dotenv(DB_DIR / ".env")


def parse_connection_string(cs: str) -> dict:
    def get(name: str) -> str:
        m = re.search(rf"(?:^|;)\s*{name}\s*=\s*([^;]+)", cs, re.IGNORECASE)
        return m.group(1).strip() if m else ""

    server = get("Server") or get("Data Source")
    server = re.sub(r"^tcp:", "", server, flags=re.IGNORECASE)
    port = 1433
    if "," in server:
        parts = server.rsplit(",", 1)
        server = parts[0].strip()
        try:
            port = int(parts[1].strip())
        except ValueError:
            pass

    return {
        "server": server,
        "port": port,
        "database": get("Initial Catalog") or get("Database"),
        "user": get("User ID") or get("UID"),
        "password": get("Password") or get("Pwd"),
    }


def get_connection_kwargs() -> dict:
    cs = os.getenv("AZURE_SQL_CONNECTION_STRING", "").strip()
    if cs:
        return parse_connection_string(cs)

    server = os.getenv("AZURE_SQL_SERVER", "").strip()
    database = os.getenv("AZURE_SQL_DATABASE", "").strip()
    user = os.getenv("AZURE_SQL_USER", "").strip()
    password = os.getenv("AZURE_SQL_PASSWORD", "")
    port = int(os.getenv("AZURE_SQL_PORT", "1433"))

    if not server or not database or not user:
        print(
            "ERROR: Set AZURE_SQL_* in db/.env (copy from AiDecisionMakingBackend/db/.env)",
            file=sys.stderr,
        )
        sys.exit(1)

    return {
        "server": server,
        "port": port,
        "database": database,
        "user": user,
        "password": password,
    }


def split_go_batches(sql: str) -> list[str]:
    batches = re.split(r"^\s*GO\s*$", sql, flags=re.MULTILINE | re.IGNORECASE)
    return [b.strip() for b in batches if b.strip()]


def main() -> None:
    import pymssql

    kwargs = get_connection_kwargs()
    sql_files = sorted(DB_DIR.glob("V*.sql"))
    if not sql_files:
        print("No V*.sql files found in", DB_DIR)
        return

    print(f"Connecting to {kwargs['server']}:{kwargs['port']}/{kwargs['database']} ...")
    conn = pymssql.connect(**kwargs)
    cursor = conn.cursor()

    for path in sql_files:
        print(f"\n--- {path.name} ---")
        batches = split_go_batches(path.read_text(encoding="utf-8"))
        for i, batch in enumerate(batches, 1):
            cursor.execute(batch)
            conn.commit()
            print(f"  OK batch {i}/{len(batches)}")

    cursor.close()
    conn.close()
    print("\nAll migrations finished.")


if __name__ == "__main__":
    main()
