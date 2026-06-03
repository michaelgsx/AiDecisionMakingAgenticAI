#!/usr/bin/env python3
"""Re-sync table_description / column_description from sys.tables + sys.columns."""

import sys
from pathlib import Path

DB_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(DB_DIR))

from run_migrations import get_connection_kwargs  # noqa: E402


def main() -> None:
    import pymssql

    kwargs = get_connection_kwargs()
    print(f"Refreshing descriptions on {kwargs['server']}/{kwargs['database']} ...")
    conn = pymssql.connect(**kwargs)
    cursor = conn.cursor()
    cursor.execute("EXEC dbo.usp_refresh_table_column_descriptions")
    conn.commit()

    cursor.execute(
        "SELECT COUNT(*) FROM dbo.table_description WHERE enabled = 1"
    )
    table_count = cursor.fetchone()[0]
    cursor.execute(
        "SELECT COUNT(*) FROM dbo.column_description WHERE enabled = 1"
    )
    col_count = cursor.fetchone()[0]

    cursor.close()
    conn.close()
    print(f"Done: {table_count} tables, {col_count} columns.")


if __name__ == "__main__":
    main()
