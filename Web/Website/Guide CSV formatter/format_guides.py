import pandas as pd
import json
import markdown
from bs4 import BeautifulSoup
import argparse

def format_sections_to_markdown(row):
    """
    Combines all section titles and content into a single markdown string.
    """
    markdown_content = ""
    
    # Process each section (1 through 9)
    for i in range(1, 10):
        title = row.get(f'section_{i}_title')
        content = row.get(f'section_{i}_content')
        
        # Only add section if title exists and isn't empty
        if pd.notna(title) and str(title).strip():
            markdown_content += f"# {title}\n\n{content}\n\n"
    
    return markdown_content.strip()

def format_resources_to_markdown(resources_json):
    """
    Converts resources JSON string to markdown list.
    """
    try:
        # Parse resources JSON string
        if pd.isna(resources_json) or not resources_json.strip():
            return ""
            
        resources = json.loads(resources_json)
        
        if not resources:
            return ""
            
        # Create markdown list
        markdown_resources = "# Resources\n\nHere are some additional resources:\n\n"
        for title, link in resources.items():
            markdown_resources += f"* [{title}]({link})\n"
            
        return markdown_resources
        
    except json.JSONDecodeError:
        print(f"Warning: Could not parse resources JSON: {resources_json}")
        return ""

def convert_markdown_to_html(markdown_text):
    """
    Convert markdown to HTML and clean up the output for Webflow compatibility
    """
    # Convert markdown to HTML using Python-Markdown
    html = markdown.markdown(
        markdown_text,
        extensions=['extra']  # Includes tables, footnotes, etc.
    )
    
    # Parse HTML with BeautifulSoup for cleaning
    soup = BeautifulSoup(html, 'html.parser')
    
    # Remove empty id attributes
    for tag in soup.find_all(attrs={'id': ''}):
        del tag['id']
    
    # Ensure proper line breaks
    # Replace double newlines with paragraph breaks
    html = str(soup).replace('\n\n', '</p><p>')
    
    return html

def transform_csv(input_file, output_file, additional_guides=None):
    """
    Main function to transform the input CSV to Webflow format.
    
    Args:
        input_file: Path to the program guides rows CSV
        output_file: Path to save the output CSV
        additional_guides: Optional path to a second CSV with additional guides
    """
    try:
        # Read input CSV
        df = pd.read_csv(input_file)
        
        # Create new dataframe with required columns
        new_df = pd.DataFrame()
        new_df['id'] = df['id']
        new_df['title'] = df['title']
        new_df['subtitle'] = df['subtitle']
        new_df['thumbnail'] = df['thumbnail']
        new_df['date'] = df['published_on']
        
        # Process each row to create content
        def process_row(row):
            # Combine sections content
            markdown_content = format_sections_to_markdown(row)
            
            # Add resources section if exists
            resources_markdown = format_resources_to_markdown(row['resources'])
            if resources_markdown:
                markdown_content += f"\n\n{resources_markdown}"
                
            # Convert final markdown to HTML
            return convert_markdown_to_html(markdown_content)
        
        # Apply processing to each row
        new_df['content'] = df.apply(process_row, axis=1)
        
        # Process additional guides if provided
        if additional_guides:
            try:
                # Read the additional guides CSV
                additional_df = pd.read_csv(additional_guides)
                
                # Create a new DataFrame for the additional guides
                add_guides_df = pd.DataFrame()
                add_guides_df['id'] = additional_df['id']
                add_guides_df['title'] = additional_df['title']
                add_guides_df['subtitle'] = additional_df['subtitle']
                add_guides_df['thumbnail'] = additional_df['thumbnail']
                
                # Handle date column - use a default if not present
                if 'date' in additional_df.columns:
                    add_guides_df['date'] = additional_df['date']
                else:
                    add_guides_df['date'] = pd.NaT  # Empty dates
                
                # Process the markdown body to HTML
                add_guides_df['content'] = additional_df['body'].apply(convert_markdown_to_html)
                
                # Concatenate the two dataframes
                new_df = pd.concat([new_df, add_guides_df], ignore_index=True)
                
                print(f"Added {len(additional_df)} additional guides from {additional_guides}")
                
            except Exception as e:
                print(f"Error processing additional guides: {str(e)}")
        
        # Save to new CSV
        new_df.to_csv(output_file, index=False)
        print(f"Successfully transformed CSV and saved to {output_file}")
        
    except Exception as e:
        print(f"Error processing CSV: {str(e)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Transform program guides CSV to Webflow format')
    parser.add_argument('--input', default="./program_guides_rows.csv", help='Input CSV file with program guides')
    parser.add_argument('--output', default="guides_cms.csv", help='Output CSV file for Webflow')
    parser.add_argument('--additional', help='Optional additional guides CSV file')
    
    args = parser.parse_args()
    
    transform_csv(args.input, args.output, args.additional)