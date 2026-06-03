# Community Seeding Tool: Usage Guide

This guide explains how to use the community seeding tool to populate your database with fake users, posts, and comments.

## Files Overview

1. **Required Files**:
   - Excel workbook (.xlsx) with:
     - "Users" sheet - List of fake users with their names and emojis
     - "Content" sheet - List of posts and comments with associated metadata
   - `Tags.csv` - List of predefined tags from your database

2. **Python Script**:
   - `community_seeding_script.py` - Script that converts Excel data to SQL
   - `create_excel_template.py` - Script that creates Excel spreadsheet

## Step 1: Prepare Your Excel File

Create an Excel workbook with two sheets:

**Users Sheet**:
- Column "name": Display name of the user
- Column "emoji": Emoji associated with the user

**Content Sheet**:
- Column "type": Either "POST" or "COMMENT"
- Column "author": Name of the user creating the post/comment (must match a name in Users sheet)
- Column "title": The text title of the post, leave blank for comment
- Column "content": The text content of the post or comment
- Column "created_date": Date in YYYY-MM-DD format
- Column "tags": Comma-separated list of tags (for posts only, must match existing tags in Tags.csv)
- Column "reply_to": For nested comments, the name of the author being replied to

You can use the included `create_excel_template.py` script to generate a sample Excel file:

```bash
python3 create_excel_template.py
```

## Step 2: Run the Script

Run the script with your prepared Excel file and tags CSV:

```bash
python3 community_seeding_script.py --excel community_seeding_template.xlsx --tags "Tags.csv" --output community_seed.sql
```

## Step 3: Run the SQL

The script will generate a SQL file with all the necessary INSERT statements. You can run this SQL file against your database:

```bash
psql -d your_database_name -f community_seed.sql
```

## Tips

1. **Chronological Order**: Arrange posts and comments in chronological order in your spreadsheet for easier understanding.
2. **Nested Comments**: To create a nested comment (reply to another comment), put the author name of the parent comment in the "reply_to" column.
3. **Tags**: Only include tags for rows with type "POST". Leave blank for comments. Only tags that already exist in your "Tags.csv" file will be used.
4. **Escape Characters**: Be careful with quotes in your content. The script handles basic escaping, but complex cases might need manual adjustment.

## Troubleshooting

- If you get an error about a user not found, make sure the author names in the Content sheet exactly match the names in the Users sheet.
- If a nested comment's parent can't be found, the comment will be created as a top-level comment instead.
- If a post has tags that don't exist in your tags CSV file, a warning will be displayed, but the post will still be created (the invalid tags will be ignored).