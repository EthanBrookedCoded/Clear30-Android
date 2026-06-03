import pandas as pd
import uuid
import re
import random
from datetime import datetime, timezone
import os
import argparse

def generate_user_id(name):
    """Generate a simple user ID based on name."""
    # Remove spaces and special characters, convert to lowercase
    clean_name = re.sub(r'[^a-zA-Z0-9]', '', name).lower()
    # Add a random suffix to ensure uniqueness
    suffix = str(uuid.uuid4())[:8]
    return f"c_{clean_name}_{suffix}"

def get_random_emoji():
    """Return a random emoji for reactions."""
    emojis = [
        "🚀", "✨", "🔥", "💪", "🌟", "🎯", "🏆", "🥇", "🎖️", "🏅",
        "📈", "⏳", "🕰️", "🛠️", "🌱", "⛰️", "🔄", "🌊", "⚡", "🏔️",
        "🌈", "🎢", "🎭", "📚", "🧠", "💡", "🌻", "🎨", "🏃‍♂️", "🧘‍♂️",
        "🌿", "☀️", "🎵", "🕊️", "💖", "💭", "🤝", "❤️", "🙌", "👏",
        "🤗", "💞", "⬆️", "✅", "☑️", "😆", "😇", "😊", "😁", "🔥",
         "⚡", "💫", "✨", "🌟","💰", "💸", "💵", "💳", "💲"

    ]
    return random.choice(emojis)

def generate_sql(users_df, content_df, tags_df=None):
    """Generate SQL statements from the spreadsheet data."""
    sql_statements = []
    
    # Dictionary to store user ID mappings (name -> id)
    user_ids = {}
    
    # Process users
    sql_statements.append("-- Insert Users")
    for _, row in users_df.iterrows():
        user_id = generate_user_id(row['name'])
        user_ids[row['name']] = user_id
        
        sql = f"""
INSERT INTO public.users (id, name, emoji)
VALUES ('{user_id}', '{row['name']}', '{row['emoji']}');
"""
        sql_statements.append(sql)
    
    # Dictionary to store post IDs for each post index
    post_ids = {}
    # Dictionary to store comment IDs by author for tracking replies
    comment_ids = {}
    current_post_id = None
    
    # Process content (posts and comments)
    sql_statements.append("\n-- Insert Posts and Comments")
    
    for idx, row in content_df.iterrows():
        content_type = row['type']
        author = row['author']
        title = str(row['title']).replace("'", "''").strip() if not pd.isna(row['title']) else ''
        content = str(row['content']).replace("'", "''").strip() if not pd.isna(row['content']) else ''
        created_date = row['created_date']
        reply_to = row['reply_to'] if not pd.isna(row['reply_to']) else ''
        
        # Format the timestamp for PostgreSQL
        if isinstance(created_date, datetime):
            # If already a datetime, format it properly
            timestamp = created_date.strftime('%Y-%m-%dT12:00:00+00:00')
        elif pd.notnull(created_date):
            # Handle pandas datetime or string formats
            try:
                if isinstance(created_date, str) and ' ' in created_date:
                    # Handle format like "2025-03-10 00:00:00"
                    date_part = created_date.split()[0]
                    timestamp = f"{date_part}T12:00:00+00:00"
                else:
                    # Try to use the value directly
                    timestamp = f"{created_date}T12:00:00+00:00"
            except:
                # Fallback to current date if parsing fails
                timestamp = datetime.now(timezone.utc).strftime('%Y-%m-%dT12:00:00+00:00')
                print(f"Warning: Invalid date format for {created_date} (type: {type(created_date)}). Using current date.")
        else:
            # Handle null/NaN dates
            timestamp = datetime.now(timezone.utc).strftime('%Y-%m-%dT12:00:00+00:00')
            print(f"Warning: Missing date for row {idx+2}. Using current date.")
        
        # Get the user ID for this author
        if author in user_ids:
            user_id = user_ids[author]
        else:
            print(f"Warning: User '{author}' not found in users sheet. Skipping row {idx+2}.")
            continue
        
        if content_type == 'POST':
            # Generate a new UUID for this post
            post_id = str(uuid.uuid4())
            current_post_id = post_id
            post_ids[idx] = post_id
            
            # Reset comment IDs for each new post
            comment_ids = {}
            
            # Extract tags if any
            tags = []
            if 'tags' in row and not pd.isna(row['tags']) and row['tags']:
                tags = [tag.strip() for tag in str(row['tags']).split(',')]
            
            # Insert post
            sql = f"""
INSERT INTO community.posts (id, user_id, title, content_type, body, created_at, view_count)
VALUES ('{post_id}', '{user_id}', '{title}', 'text', '{content}', '{timestamp}', {random.randint(100, 300)});
"""
            sql_statements.append(sql)

            # Make sure we have tags data
            if tags_df is not None and tags:
                # Insert tags and post_tags
                for tag in tags:
                    tag_id = None

                    # Check if tag exists in the predefined tags
                    if tag in tags_df['name'].values:
                        tag_id = tags_df[tags_df['name'] == tag].iloc[0]['id']
                    else:
                        print(f"Warning: Tag '{tag}' not found in tags sheet. Skipping.")
                        continue

                    # Associate tag with post
                    sql_statements.append(f"""
-- Associate tag with post
INSERT INTO community.post_tags (post_id, tag_id, created_at)
VALUES ('{post_id}', '{tag_id}', '{timestamp}');
""")
            
            # Add reactions to this post
            # Randomly decide whether to use 1 or 2 different emojis
            num_emoji_types = random.randint(1, 2)
            emoji_list = []
            for _ in range(num_emoji_types):
                emoji_list.append(get_random_emoji())
            
            # Make sure we don't have duplicate emojis if we selected 2
            if num_emoji_types == 2 and emoji_list[0] == emoji_list[1]:
                while emoji_list[0] == emoji_list[1]:
                    emoji_list[1] = get_random_emoji()
                    
            # Total number of reactions for this post
            num_reactions = random.randint(7, 20)
            
            sql_statements.append(f"\n-- Insert Reactions for post {post_id}")
            # Get a list of user IDs who can react (excluding the author)
            available_reactors = [uid for name, uid in user_ids.items() if name != author]
            
            # Ensure we don't try to generate more reactions than available users
            num_reactions = min(num_reactions, len(available_reactors))
            
            # Select random users to react
            reacting_users = random.sample(available_reactors, num_reactions)
            
            if num_emoji_types == 1:
                # Use the same emoji for all reactions
                for reactor_id in reacting_users:
                    sql_statements.append(f"""
INSERT INTO community.reactions (user_id, post_id, emoji, created_at)
VALUES ('{reactor_id}', '{post_id}', '{emoji_list[0]}', '{timestamp}');
""")
            else:
                # Distribute reactions between the two emojis
                # Calculate split point - randomly distribute between the two emojis
                split_point = random.randint(1, num_reactions - 1)
                
                # First group of reactions with first emoji
                for reactor_id in reacting_users[:split_point]:
                    sql_statements.append(f"""
INSERT INTO community.reactions (user_id, post_id, emoji, created_at)
VALUES ('{reactor_id}', '{post_id}', '{emoji_list[0]}', '{timestamp}');
""")
                
                # Second group of reactions with second emoji
                for reactor_id in reacting_users[split_point:]:
                    sql_statements.append(f"""
INSERT INTO community.reactions (user_id, post_id, emoji, created_at)
VALUES ('{reactor_id}', '{post_id}', '{emoji_list[1]}', '{timestamp}');
""")
            
        elif content_type == 'COMMENT' and current_post_id:
            # Generate a UUID for this comment
            comment_id = str(uuid.uuid4())
            
            # Store this comment ID by author for potential replies
            comment_ids[author] = comment_id
            
            # Check if this is a reply to another comment
            parent_comment_id = 'NULL'
            if reply_to and reply_to in comment_ids:
                parent_comment_id = f"'{comment_ids[reply_to]}'"
                print(f"Setting comment by {author} as reply to comment by {reply_to}")
            
            # Insert comment with parent_comment_id if it's a reply
            sql = f"""
INSERT INTO community.comments (id, post_id, user_id, body, created_at, parent_comment_id)
VALUES ('{comment_id}', '{current_post_id}', '{user_id}', '{content}', '{timestamp}', {parent_comment_id});
"""
            sql_statements.append(sql)
    
    return "\n".join(sql_statements)

def main():
    """Main function to process the Excel spreadsheet and generate SQL."""
    import os
    import argparse
    
    # Setup command line arguments
    parser = argparse.ArgumentParser(description='Generate SQL from community seeding Excel spreadsheet')
    parser.add_argument('--excel', required=True, help='Path to Excel file with Users and Content sheets')
    parser.add_argument('--tags', required=True, help='Path to predefined tags CSV file')
    parser.add_argument('--output', default='community_seeding.sql', help='Output SQL file path')
    
    args = parser.parse_args()
    
    # Check if files exist
    if not os.path.exists(args.excel):
        print(f"Error: Excel file '{args.excel}' not found")
        return
        
    if not os.path.exists(args.tags):
        print(f"Error: Tags file '{args.tags}' not found")
        return
    
    try:
        # Load tags data
        tags_df = pd.read_csv(args.tags)
        print(f"Loaded {len(tags_df)} tags from '{args.tags}'")
        
        # Load Excel sheets
        users_df = pd.read_excel(args.excel, sheet_name='Users')
        content_df = pd.read_excel(args.excel, sheet_name='Content')
        print(f"Loaded {len(users_df)} users and {len(content_df)} content items from '{args.excel}'")
        
        # Print some diagnostic info about replies
        reply_count = content_df[~pd.isna(content_df['reply_to']) & (content_df['reply_to'] != '')].shape[0]
        print(f"Found {reply_count} replies in the content sheet")
    except Exception as e:
        print(f"Error reading input files: {e}")
        return
    
    # Generate SQL statements
    sql = generate_sql(users_df, content_df, tags_df)
    
    # Write SQL to file
    with open(args.output, "w") as f:
        f.write(sql)
    
    print(f"SQL generated successfully and saved to {args.output}")
    posts_count = len(content_df[content_df['type'] == 'POST'])
    comments_count = len(content_df[content_df['type'] == 'COMMENT'])
    print(f"Generated {len(users_df)} users, {posts_count} posts with {comments_count} comments")
    print(f"Each post has between 7-20 reactions with 1-2 random emojis")

if __name__ == "__main__":
    main()