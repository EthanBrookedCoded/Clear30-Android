import os
import shutil
import subprocess
import re

# Define input and output directories
webflow_export = "webflow_export"
output = "output"
program_pages = "program_pages"
files = "files"
cdn_folder = "cdn.prod.website-files.com"
filed_to_rem = ["_downloads.html", "robots.ssl.txt"]

# Remove existing output folder
if os.path.exists(output):
    shutil.rmtree(output)

# Ensure output directory exists
if not os.path.exists(output):
    os.makedirs(output)

# Helper function to update references in files
def update_references(file_path, cdn_folder_name, deeper=False):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    if deeper:
        content = content.replace(f"./{cdn_folder_name}", f"../{cdn_folder_name}")
    else:
        content = content.replace(f"../{cdn_folder_name}", f"./{cdn_folder_name}")

    # Remove .html extension from links
    content = content.replace(".html", "")

    # Find all <a href=" where the link does not start with ., http, or /
    # Add / to the start
    def replace_link(match):
        link = match.group(1)
        if not (link.startswith(".") or link.startswith("http") or link.startswith("/") or link.startswith("#") or link.startswith("mailto:")):
            return f'<a href="/{link}"'
        return match.group(0)
    content = re.sub(r'<a\s+href="([^"]+)"', replace_link, content)

    # Handle sub dir nav links (start with .. from site downlaod)
    content = content.replace("<a href=\"..", "<a href=\"")

    # Replace /index with /
    content = content.replace("/index\"", "/\"")

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

# Step 1: Copy webflow_export to output
shutil.copytree(webflow_export, output, dirs_exist_ok=True)

# Step 2: Move the CDN folder into the website folder
cdn_src = os.path.join(output, cdn_folder)

website_folder = None
for item in os.listdir(output):
    item_path = os.path.join(output, item)
    if os.path.isdir(item_path) and item != cdn_folder:
        website_folder = item_path
        break

if website_folder is None:
    raise Exception("No website folder found in the output directory")

cdn_dest = os.path.join(website_folder, cdn_folder)

if os.path.exists(cdn_src):
    shutil.move(cdn_src, cdn_dest)

# Delete files from the website folder
for file in filed_to_rem:
    file_path = os.path.join(website_folder, file)
    if os.path.exists(file_path):
        os.remove(file_path)

# PROCESS FILES
def process_directory(directory):
    skip_index_in = [] # Skip index.html in these directories

    for root, dirs, files in os.walk(directory):
        for file in files:
            file_path = os.path.join(root, file)

            # Update references for all files
            if file.endswith(('.html')) and (file != "index.html" or root not in skip_index_in):
                update_references(file_path, cdn_folder)

            # Restructure HTML files
            if file.endswith('.html') and file != "index.html":

                base_name = os.path.splitext(file)[0]
                new_folder_path = os.path.join(root, base_name)

                # Check if the directory already exists
                if os.path.exists(new_folder_path):
                    skip_index_in.append(new_folder_path)

                # Create new folder
                os.makedirs(new_folder_path, exist_ok=True)

                # Move and rename the file
                new_file_path = os.path.join(new_folder_path, "index.html")
                shutil.move(file_path, new_file_path)

                # # Update references
                update_references(new_file_path, cdn_folder, deeper=True)

process_directory(website_folder)


# WEBFLOW WATERMARK
def remove_webflow_watermark(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith('.css'):
                css_file_path = os.path.join(root, file)
                with open(css_file_path, "a", encoding="utf-8") as f:
                    f.write("\n.w-webflow-badge {\n   display: none !important;\n   visibility: hidden !important;\n}\n")
                return

remove_webflow_watermark(website_folder)


# PROGRAM PAGES
def add_program_pages(directory):
    base_dir = os.getcwd()
    program_pages_dir = os.path.join(base_dir, program_pages)

    # Build the program pages
    os.chdir(program_pages_dir)
    subprocess.run(["npm", "install"], check=True)
    subprocess.run(["npm", "run", "build:production"], check=True)

    # Copy the dist folder to the output directory
    os.chdir(base_dir)
    dist_dir = os.path.join(program_pages_dir, "dist")
    shutil.copytree(dist_dir, os.path.join(directory, "program"), dirs_exist_ok=True)

# add_program_pages(website_folder)


# SITE FILES
def copy_site_files(directory):
    base_dir = os.getcwd()
    files_dir = os.path.join(base_dir, files)

    shutil.copytree(files_dir, os.path.join(directory, "files"), dirs_exist_ok=True)

copy_site_files(website_folder)


print("Website export transformation completed. Output available in the 'output' folder.")
