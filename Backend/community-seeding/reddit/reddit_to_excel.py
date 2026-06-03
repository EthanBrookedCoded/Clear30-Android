import json
import pandas as pd
import random
from datetime import datetime, timedelta
import sys
import os

# List of generic names to use instead of Reddit usernames
GENERIC_NAMES = [
    "Michael", "Sarah", "David", "Jessica", "James", "Emily", "John", "Jennifer", 
    "Robert", "Lisa", "William", "Mary", "Richard", "Patricia", "Joseph", "Linda",
    "Daniel", "Barbara", "Charles", "Elizabeth", "Thomas", "Susan", "Christopher", 
    "Karen", "Matthew", "Nancy", "Anthony", "Carol", "Mark", "Laura"
]

# Default emoji for all users
DEFAULT_EMOJI = "😁"

# Default tag for all posts
DEFAULT_TAG = "Clear30"

def parse_reddit_json(json_data):
    """Parse Reddit JSON data and extract posts and comments."""
    data = json.loads(json_data) if isinstance(json_data, str) else json_data
    
    posts = []
    comments = []
    
    # Reddit API can return data in different formats
    # If it's a single post with comments
    if isinstance(data, list) and len(data) == 2 and 'kind' in data[0] and data[0]['kind'] == 'Listing':
        # First item is usually the post
        for child in data[0]['data']['children']:
            if child['kind'] == 't3':  # Post
                posts.append(child['data'])
        
        # Second item is usually the comments
        if len(data) > 1 and 'kind' in data[1] and data[1]['kind'] == 'Listing':
            for child in data[1]['data']['children']:
                if child['kind'] == 't1':  # Comment
                    extract_comments(child['data'], comments)
    
    # If it's a list of posts
    elif isinstance(data, dict) and 'kind' in data and data['kind'] == 'Listing':
        for child in data['data']['children']:
            if child['kind'] == 't3':  # Post
                posts.append(child['data'])
    
    # Try to handle any other format
    elif isinstance(data, list):
        for item in data:
            if isinstance(item, dict):
                if 'kind' in item and item['kind'] == 'Listing':
                    for child in item['data']['children']:
                        if child['kind'] == 't3':  # Post
                            posts.append(child['data'])
                        elif child['kind'] == 't1':  # Comment
                            extract_comments(child['data'], comments)
    
    return posts, comments

def extract_comments(comment_data, comments_list, parent_comment=None, original_parent_author=None):
    """
    Recursively extract comments and their replies.
    
    Args:
        comment_data: The comment data to process
        comments_list: List to add comments to
        parent_comment: ID of the parent comment (if this is a reply)
        original_parent_author: Author of the original top-level comment (for flattening nested replies)
    """
    current_author = comment_data['author']
    
    # If this is a top-level comment (direct reply to post), set it as the original parent
    if parent_comment is None:
        # This is a top-level comment (replying directly to the post)
        original_parent_author = current_author
    
    # Add the current comment
    comments_list.append({
        'id': comment_data['id'],
        'body': comment_data['body'],
        'author': current_author,
        'created_utc': comment_data['created_utc'],
        'parent_id': comment_data['parent_id'],
        'link_id': comment_data.get('link_id', ''),
        'is_submitter': comment_data.get('is_submitter', False),
        'parent_comment': parent_comment,
        'original_parent_author': original_parent_author  # Track the original parent for flattening
    })
    
    # Check if there are replies
    if 'replies' in comment_data and comment_data['replies'] and isinstance(comment_data['replies'], dict):
        if 'data' in comment_data['replies'] and 'children' in comment_data['replies']['data']:
            for reply in comment_data['replies']['data']['children']:
                if reply['kind'] == 't1':
                    # Always pass the original parent author down the chain for nested replies
                    extract_comments(reply['data'], comments_list, comment_data['id'], original_parent_author)

def generate_recent_date():
    """Generate a random date within the last few days."""
    days_ago = random.randint(0, 6)  # 0-6 days ago
    hours_ago = random.randint(0, 23)  # 0-23 hours ago
    
    random_date = datetime.now() - timedelta(days=days_ago, hours=hours_ago)
    return random_date.strftime('%Y-%m-%d')

def map_users_to_generic_names(users):
    """Map Reddit usernames to generic names."""
    user_mapping = {}
    
    # Ensure we don't run out of generic names
    if len(users) > len(GENERIC_NAMES):
        # If we have more users than generic names, we'll cycle through the list
        for i, user in enumerate(users):
            user_mapping[user] = GENERIC_NAMES[i % len(GENERIC_NAMES)]
    else:
        # Randomly select names without replacement
        selected_names = random.sample(GENERIC_NAMES, len(users))
        for i, user in enumerate(users):
            user_mapping[user] = selected_names[i]
    
    return user_mapping

def create_users_data(user_mapping):
    """Create data for the Users sheet."""
    users_data = {
        'name': list(user_mapping.values()),
        'emoji': [DEFAULT_EMOJI] * len(user_mapping)
    }
    return users_data

def create_content_data(posts, comments, user_mapping):
    """Create data for the Content sheet, maintaining post-comment relationships."""
    content_data = {
        'type': [],
        'author': [],
        'content': [],
        'title': [],
        'created_date': [],
        'tags': [],
        'reply_to': []
    }
    
    # Create a mapping of post IDs to their comments
    post_comments = {}
    post_id_by_full_name = {}
    
    # Create a mapping of post IDs by their full name (t3_postid)
    for post in posts:
        post_id_by_full_name[f"t3_{post['id']}"] = post['id']
    
    # First attempt to map comments to posts using link_id
    for comment in comments:
        link_id = comment.get('link_id', '')
        if link_id and link_id.startswith('t3_'):
            post_id = link_id[3:]  # Remove 't3_' prefix
            if post_id not in post_comments:
                post_comments[post_id] = []
            post_comments[post_id].append(comment)
            continue
            
        # If link_id is not available, try using parent_id for top-level comments
        parent_id = comment['parent_id']
        if parent_id.startswith('t3_'):  # Direct reply to a post
            post_id = parent_id[3:]  # Remove 't3_' prefix
            if post_id not in post_comments:
                post_comments[post_id] = []
            post_comments[post_id].append(comment)
        elif parent_id.startswith('t1_'):  # Reply to a comment
            # Find the parent comment's post
            for other_comment in comments:
                if other_comment['id'] == parent_id[3:]:  # Remove 't1_' prefix
                    if 'link_id' in other_comment and other_comment['link_id'].startswith('t3_'):
                        post_id = other_comment['link_id'][3:]
                        if post_id not in post_comments:
                            post_comments[post_id] = []
                        post_comments[post_id].append(comment)
                        break
    
    # Fallback: If some comments couldn't be matched to posts, assign them to the first post
    orphaned_comments = []
    for comment in comments:
        found = False
        for post_id, post_comment_list in post_comments.items():
            if comment in post_comment_list:
                found = True
                break
        if not found:
            orphaned_comments.append(comment)
    
    if orphaned_comments and posts:
        default_post_id = posts[0]['id']
        if default_post_id not in post_comments:
            post_comments[default_post_id] = []
        post_comments[default_post_id].extend(orphaned_comments)
        print(f"Note: {len(orphaned_comments)} comments couldn't be matched to their posts and were assigned to the first post.")
    
    # Create a mapping of comment IDs to authors for reply relationships
    comment_author_mapping = {comment['id']: comment['author'] for comment in comments}
    
    # Process posts and their comments together
    for post in posts:
        # Add the post
        content_data['type'].append('POST')
        content_data['author'].append(user_mapping.get(post['author'], random.choice(list(user_mapping.values()))))
        content_data['content'].append(post['selftext'])
        content_data['title'].append(post['title'])
        content_data['created_date'].append(generate_recent_date())
        content_data['tags'].append(DEFAULT_TAG)
        content_data['reply_to'].append('')
        
        # Add comments for this post
        post_id = post['id']
        if post_id in post_comments:
            # Sort comments by created_utc to maintain chronological order
            sorted_comments = sorted(post_comments[post_id], key=lambda x: x['created_utc'])
            
            for comment in sorted_comments:
                content_data['type'].append('COMMENT')
                content_data['author'].append(user_mapping.get(comment['author'], random.choice(list(user_mapping.values()))))
                content_data['content'].append(comment['body'])
                content_data['title'].append('')  # Comments don't have titles
                content_data['created_date'].append(generate_recent_date())
                content_data['tags'].append('')  # Comments don't have tags
                
                # Handle reply relationships 
                # If this comment has an original_parent_author (meaning it's part of a nested reply chain),
                # use that as the reply_to value instead of the immediate parent
                if 'original_parent_author' in comment and comment['original_parent_author'] and comment['author'] != comment['original_parent_author']:
                    original_parent = comment['original_parent_author']
                    content_data['reply_to'].append(user_mapping.get(original_parent, ''))
                    print(f"Setting comment by {comment['author']} as reply to original parent {original_parent}")
                elif comment['parent_id'].startswith('t1_'):  # Reply to a comment (first level)
                    parent_comment_id = comment['parent_id'][3:]  # Remove 't1_' prefix
                    parent_author = comment_author_mapping.get(parent_comment_id, '')
                    content_data['reply_to'].append(user_mapping.get(parent_author, ''))
                else:
                    content_data['reply_to'].append('')
    
    return content_data

def process_reddit_json_files(file_paths, output_file='reddit_community_data.xlsx'):
    """Process multiple Reddit JSON files and create an Excel file."""
    all_posts = []
    all_comments = []
    all_users = set()
    
    # Process each file
    for file_path in file_paths:
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                json_data = f.read()
            
            # Try to parse the JSON
            try:
                posts, comments = parse_reddit_json(json_data)
                print(f"From {file_path}:")
                print(f"  - Found {len(posts)} posts")
                if posts:
                    for i, post in enumerate(posts):
                        print(f"    {i+1}. '{post['title']}' by u/{post['author']}")
                
                print(f"  - Found {len(comments)} comments")
                
                all_posts.extend(posts)
                all_comments.extend(comments)
                
                # Collect all unique usernames
                for post in posts:
                    all_users.add(post['author'])
                for comment in comments:
                    all_users.add(comment['author'])
            except json.JSONDecodeError:
                print(f"Error: File '{file_path}' contains invalid JSON")
                continue
            except Exception as e:
                print(f"Error parsing JSON in '{file_path}': {str(e)}")
                continue
        except Exception as e:
            print(f"Error processing file '{file_path}': {str(e)}")
            continue
    
    if not all_posts and not all_comments:
        print("No valid posts or comments found in the provided files.")
        return None
    
    print(f"\nProcessing {len(all_posts)} posts and {len(all_comments)} comments from {len(all_users)} unique users...")
    
    # Map Reddit usernames to generic names
    user_mapping = map_users_to_generic_names(all_users)
    
    # Create data for Excel
    users_data = create_users_data(user_mapping)
    content_data = create_content_data(all_posts, all_comments, user_mapping)
    
    # Create DataFrames
    users_df = pd.DataFrame(users_data)
    content_df = pd.DataFrame(content_data)
    
    # Create Excel file
    try:
        with pd.ExcelWriter(output_file) as writer:
            users_df.to_excel(writer, sheet_name='Users', index=False)
            content_df.to_excel(writer, sheet_name='Content', index=False)
        
        post_count = len(content_df[content_df['type'] == 'POST'])
        comment_count = len(content_df[content_df['type'] == 'COMMENT'])
        
        print(f"\nExcel file created: {output_file}")
        print(f"Generated {len(users_df)} users, {post_count} posts, and {comment_count} comments")
        
        # Count comments per post for verification
        if post_count > 0:
            print("\nComments distribution:")
            post_indices = content_df[content_df['type'] == 'POST'].index.tolist()
            for i in range(len(post_indices)):
                start_idx = post_indices[i]
                end_idx = post_indices[i+1] if i < len(post_indices) - 1 else len(content_df)
                post_title = content_df.loc[start_idx, 'title']
                comment_count = end_idx - start_idx - 1
                print(f"  - '{post_title[:30]}{'...' if len(post_title) > 30 else ''}': {comment_count} comments")
        
        # Provide a summary of the generic name mapping
        print("\nReddit username to generic name mapping:")
        for reddit_user, generic_name in user_mapping.items():
            print(f"  {reddit_user} -> {generic_name}")
            
        return output_file
    except Exception as e:
        print(f"Error creating Excel file: {str(e)}")
        return None

def main():
    """Main function to run the script."""
    import argparse
    
    parser = argparse.ArgumentParser(description='Convert Reddit JSON data to an Excel file for community seeding.')
    parser.add_argument('files', nargs='*', help='JSON files containing Reddit data')
    parser.add_argument('--paste', action='store_true', help='Read JSON data from standard input (for pasting)')
    parser.add_argument('--output', default='reddit_community_data.xlsx', help='Output Excel file name')
    parser.add_argument('--tag', default='Clear30', help='Default tag to use for posts')
    
    args = parser.parse_args()
    
    # Update the default tag if specified
    global DEFAULT_TAG
    DEFAULT_TAG = args.tag
    
    # Process files or pasted data
    if args.paste:
        print("Paste your Reddit JSON data below and press Ctrl+D (Unix) or Ctrl+Z (Windows) when finished:")
        try:
            json_data = sys.stdin.read()
            
            # Save to a temporary file so we can process it uniformly
            temp_file = 'temp_reddit_data.json'
            with open(temp_file, 'w', encoding='utf-8') as f:
                f.write(json_data)
            
            output_file = process_reddit_json_files([temp_file], args.output)
            
            # Clean up temporary file
            try:
                os.remove(temp_file)
            except:
                pass
        except KeyboardInterrupt:
            print("\nOperation cancelled.")
            sys.exit(1)
    elif args.files:
        # Validate files exist
        for file_path in args.files:
            if not os.path.exists(file_path):
                print(f"Error: File '{file_path}' not found")
                sys.exit(1)
        
        # Process files
        output_file = process_reddit_json_files(args.files, args.output)
    else:
        parser.print_help()
        sys.exit(1)
    
    if output_file:
        print(f"\nYou can now use this Excel file with the community seeding script:")
        print(f"python community_seeding_script.py --excel {output_file} --tags \"Clear30 Tags Rows.csv\" --output reddit_community_seed.sql")

if __name__ == "__main__":
    main()