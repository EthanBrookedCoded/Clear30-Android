# Reddit JSON to Excel Converter: Usage Guide

This guide explains how to use the Reddit JSON to Excel converter script to import Reddit content for your community seeding tool.

## Purpose

This script converts Reddit post and comment JSON data into an Excel file that can be used with the community seeding script to populate your database with realistic content.

## Features

- Converts Reddit posts and comments to the format expected by the community seeding script
- Replaces Reddit usernames with generic names for privacy and consistency
- Assigns recent dates to all content
- Handles nested comments (limiting to one level of nesting)
- Supports multiple input files
- Can accept pasted JSON data directly

## Requirements

- Python 3.6+
- pandas
- Required by the community seeding script: "Clear30 Tags Rows.csv"

## Usage Options

### Option 1: Using JSON Files

If you have saved Reddit JSON data to files:

```bash
python3 reddit_to_excel.py file1.json file2.json
```

### Option 2: Pasting JSON Data

If you want to paste JSON data directly:

```bash
python3 reddit_to_excel.py --paste
```

Then paste your JSON data and press Ctrl+D (Unix) or Ctrl+Z followed by Enter (Windows) when finished.

### Option 3: Customizing Output

You can customize the output file name and default tag:

```bash
python3 reddit_to_excel.py --output custom_name.xlsx --tag "Motivation" file1.json
```

## How to Get Reddit JSON Data

1. **Using Reddit API**: If you have API access, this is the cleanest method.

2. **Using the JSON Endpoint**:
   - Go to any Reddit thread
   - Add `.json` to the end of the URL
   - Example: `https://www.reddit.com/r/TheClear30/comments/1dyln4i/21_days_in_cravings_going_away/.json`
   - Copy the entire JSON response and save it to a file or paste it directly

3. **Using Browser Developer Tools**:
   - Open a Reddit thread
   - Right-click and select "Inspect" or press F12
   - Go to the Network tab
   - Refresh the page
   - Look for the main page request (usually the URL of the thread)
   - Select the "Response" tab
   - Copy the JSON response

## Complete Workflow

1. **Collect Reddit Data**:
   - Save Reddit JSON data to files or prepare to paste it

2. **Convert to Excel**:
   ```bash
   python reddit_to_excel.py reddit_data.json
   ```

3. **Generate SQL**:
   ```bash
   python community_seeding_script.py --excel reddit_community_data.xlsx --tags "Tags.csv" --output reddit_community_seed.sql
   ```

4. **Import to Database**:
   ```bash
   psql -d your_database_name -f reddit_community_seed.sql
   ```

## Troubleshooting

- **Invalid JSON Error**: Make sure you're copying the complete JSON response. It should start with `[` and end with `]`.
- **No Posts Found**: The script looks for specific Reddit JSON structures. Make sure you're using the `.json` endpoint or proper API responses.
- **Excel File Error**: Make sure you have write permission in the current directory.

## Example Output

The script generates an Excel file with two sheets:

1. **Users Sheet**:
   - Generic names for all Reddit users
   - 😁 emoji for all users

2. **Content Sheet**:
   - Posts and comments with proper relationships
   - Recent dates
   - Default tags for posts
   - Proper reply structure for comments