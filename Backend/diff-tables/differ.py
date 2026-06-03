#!/usr/bin/env python3
"""
CSV Diff to SQL Update Generator

This script compares two CSV files (old and new) and generates SQL UPDATE statements
for rows that have changed, avoiding duplicate ID issues when re-importing data.
"""

import pandas as pd
import argparse
import sys
from pathlib import Path


def escape_sql_value(value):
    """Escape SQL values properly for safe SQL generation."""
    if pd.isna(value) or value is None:
        return "NULL"
    elif isinstance(value, str):
        # Escape single quotes by doubling them
        escaped = value.replace("'", "''")
        return f"'{escaped}'"
    elif isinstance(value, (int, float)):
        return str(value)
    else:
        # Convert to string and escape
        escaped = str(value).replace("'", "''")
        return f"'{escaped}'"


def generate_update_statement(table_name, id_column, id_value, changes):
    """Generate a single UPDATE SQL statement."""
    set_clauses = []
    for column, new_value in changes.items():
        set_clauses.append(f"{column} = {escape_sql_value(new_value)}")
    
    set_clause = ", ".join(set_clauses)
    where_clause = f"{id_column} = {escape_sql_value(id_value)}"
    
    return f"UPDATE {table_name} SET {set_clause} WHERE {where_clause};"


def find_id_column(df):
    """Try to automatically identify the ID column."""
    possible_id_columns = ['id', 'ID', 'Id', 'user_id', 'uid', 'pk']
    
    for col in possible_id_columns:
        if col in df.columns:
            return col
    
    # If no obvious ID column, return the first column
    return df.columns[0]


def compare_csvs_and_generate_sql(old_csv, new_csv, table_name, id_column=None):
    """Compare two CSV files and generate SQL UPDATE statements."""
    try:
        # Read CSV files
        print(f"Reading {old_csv}...")
        old_df = pd.read_csv(old_csv)
        print(f"Reading {new_csv}...")
        new_df = pd.read_csv(new_csv)
        
        # Use specified ID column or verify it exists
        if id_column not in new_df.columns:
            print(f"Warning: Specified ID column '{id_column}' not found in {new_csv}")
            id_column = find_id_column(new_df)
            print(f"Auto-detected ID column: {id_column}")
        else:
            print(f"Using ID column: {id_column}")
        
        # Verify ID column exists in both files
        if id_column not in old_df.columns:
            raise ValueError(f"ID column '{id_column}' not found in {old_csv}")
        if id_column not in new_df.columns:
            raise ValueError(f"ID column '{id_column}' not found in {new_csv}")
        
        # Set ID column as index for easier comparison
        old_df.set_index(id_column, inplace=True)
        new_df.set_index(id_column, inplace=True)
        
        # Find common columns (excluding the ID column)
        common_columns = list(set(old_df.columns) & set(new_df.columns))
        
        sql_statements = []
        changes_count = 0
        
        print(f"Comparing rows...")
        
        # Compare each row in new_df with corresponding row in old_df
        for id_value in new_df.index:
            if id_value in old_df.index:
                # Row exists in both files, check for changes
                old_row = old_df.loc[id_value]
                new_row = new_df.loc[id_value]
                
                changes = {}
                for column in common_columns:
                    old_val = old_row[column] if column in old_row else None
                    new_val = new_row[column] if column in new_row else None
                    
                    # Compare values, handling NaN appropriately
                    if pd.isna(old_val) and pd.isna(new_val):
                        continue  # Both are NaN, no change
                    elif pd.isna(old_val) != pd.isna(new_val) or old_val != new_val:
                        changes[column] = new_val
                
                # Generate UPDATE statement if there are changes
                if changes:
                    sql_statement = generate_update_statement(table_name, id_column, id_value, changes)
                    sql_statements.append(sql_statement)
                    changes_count += 1
            else:
                print(f"Warning: ID {id_value} exists in new file but not in old file. Skipping INSERT (as requested).")
        
        return sql_statements, changes_count
        
    except Exception as e:
        print(f"Error processing CSV files: {e}")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="Generate SQL UPDATE statements from CSV file differences"
    )
    parser.add_argument("--old-csv", default="old.csv", help="Path to the old CSV file (default: old.csv)")
    parser.add_argument("--new-csv", default="new.csv", help="Path to the new CSV file (default: new.csv)")
    parser.add_argument("--id-column", default="id", help="Name of the ID column (default: id)")
    parser.add_argument("--output", "-o", default="out.txt", help="Output file for SQL statements (default: out.txt)")
    
    args = parser.parse_args()
    
    # Verify input files exist
    if not Path(args.old_csv).exists():
        print(f"Error: File {args.old_csv} does not exist")
        sys.exit(1)
    
    if not Path(args.new_csv).exists():
        print(f"Error: File {args.new_csv} does not exist")
        sys.exit(1)
    
    # Get table name from user
    table_name = input("Enter the table name (schema.table format): ").strip()
    if not table_name:
        print("Error: Table name cannot be empty")
        sys.exit(1)
    
    # Generate SQL statements
    sql_statements, changes_count = compare_csvs_and_generate_sql(
        args.old_csv, 
        args.new_csv, 
        table_name, 
        args.id_column
    )
    
    # Write to output file
    try:
        with open(args.output, 'w') as f:
            if sql_statements:
                f.write("-- SQL UPDATE statements generated from CSV comparison\n")
                f.write(f"-- Table: {table_name}\n")
                f.write(f"-- Total changes: {changes_count}\n\n")
                
                for statement in sql_statements:
                    f.write(statement + "\n")
                
                print(f"\n✅ Successfully generated {changes_count} UPDATE statements")
                print(f"📄 SQL statements written to: {args.output}")
            else:
                f.write("-- No changes detected between the CSV files\n")
                print("\n✅ No changes detected between the CSV files")
                print(f"📄 Empty result written to: {args.output}")
                
    except Exception as e:
        print(f"Error writing to output file: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()