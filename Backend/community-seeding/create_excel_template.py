import pandas as pd

# Create Users sheet data
users_data = {
    'name': ['Sarah', 'Michael', 'Jessica', 'Alex', 'Jordan', 'Taylor'],
    'emoji': ['🌟', '🌊', '🌻', '🔥', '🌈', '🍀']
}

# Create Content sheet data - use only valid tags that exist in your database
# Replace these example tags with actual tags from your Tags.csv
content_data = {
    'type': ['POST', 'COMMENT', 'COMMENT', 'POST', 'COMMENT', 'COMMENT', 'COMMENT', 'POST', 'COMMENT'],
    'author': ['Sarah', 'Michael', 'Jessica', 'Michael', 'Sarah', 'Alex', 'Jessica', 'Taylor', 'Jordan'],
    'title': [
        'Day 30 Celebration!', '', '', 'Social Situations', '', '', '', 'Daily Gratitude', 'Gratitude Practice'
    ],
    'content': [
        "Just completed day 30! So proud of myself and feeling stronger than ever.",
        "Congratulations Sarah! That's a huge accomplishment. Keep going!",
        "You're inspiring me to keep going on my own journey. Thank you for sharing!",
        "Need advice on dealing with social situations where everyone is drinking. What strategies have worked for you?",
        "I always bring my own non-alcoholic drink that I enjoy. Having something in my hand helps a lot.",
        "Letting close friends know beforehand has been helpful for me. True friends will support you.",
        "I schedule an early morning activity for the next day so I have a good reason to leave early if needed.",
        "Daily gratitude has been a game-changer for me. Three things I'm grateful for today: 1) A clear mind 2) Supportive community 3) New opportunities",
        "Love this! I'm going to start my own gratitude practice."
    ],
    'created_date': [
        '2023-06-15', '2023-06-15', '2023-06-16', '2023-06-17', '2023-06-17', 
        '2023-06-18', '2023-06-18', '2023-06-20', '2023-06-20'
    ],
    'tags': [
        'Clear30,Advice', '', '', 'Clear30,Support', '', '', '', 'Clear30', ''
    ],
    'reply_to': [
        '', '', 'Michael', '', '', '', 'Alex', '', ''
    ]
}

tag_data = {
    'name': ['Clear30', 'Life (Abstinence)', 'Life (Moderation)', 'Discussion', 'Support', 'Advice', 'Question', 'Day 0 to 30']
}

# Create DataFrames
users_df = pd.DataFrame(users_data)
content_df = pd.DataFrame(content_data)
tags_df = pd.DataFrame(tag_data)

# Create an Excel writer
with pd.ExcelWriter('community_seeding_template.xlsx') as writer:
    users_df.to_excel(writer, sheet_name='Users', index=False)
    content_df.to_excel(writer, sheet_name='Content', index=False)
    tags_df.to_excel(writer, sheet_name='Tags', index=False)

print("Excel template created: community_seeding_template.xlsx")
print("\nImportant: Make sure to replace the example tags in the template with actual tags from your database!")
print("Only tags that exist in 'Tags.csv' will be used.")