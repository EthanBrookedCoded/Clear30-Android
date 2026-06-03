#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

/**
 * Script to copy favicon files from shared package to all consuming apps
 * This ensures consistent favicon handling across the monorepo
 */

const sharedAssetsDir = path.join(__dirname, '../packages/shared/src/assets/images');
const appsDir = path.join(__dirname, '../packages/apps');

// Favicon files to copy
const faviconFiles = [
  'favicon.svg',
  'favicon.ico',
  'favicon.png'
];

function copyFaviconFiles() {
  console.log('🔄 Updating favicons across all apps...');
  
  // Get all app directories
  const appDirs = fs.readdirSync(appsDir, { withFileTypes: true })
    .filter(dirent => dirent.isDirectory())
    .map(dirent => dirent.name);
  
  let copiedCount = 0;
  
  appDirs.forEach(appName => {
    const appPublicDir = path.join(appsDir, appName, 'public');
    
    // Check if public directory exists
    if (!fs.existsSync(appPublicDir)) {
      console.log(`⚠️  Skipping ${appName}: no public directory found`);
      return;
    }
    
    // Copy each favicon file
    faviconFiles.forEach(filename => {
      const sourcePath = path.join(sharedAssetsDir, filename);
      const destPath = path.join(appPublicDir, filename);
      
      // Only copy if source exists
      if (fs.existsSync(sourcePath)) {
        try {
          fs.copyFileSync(sourcePath, destPath);
          console.log(`✅ Copied ${filename} to ${appName}`);
          copiedCount++;
        } catch (error) {
          console.error(`❌ Failed to copy ${filename} to ${appName}:`, error.message);
        }
      } else {
        console.log(`⚠️  Source favicon ${filename} not found in shared package`);
      }
    });
  });
  
  console.log(`\n🎉 Favicon update complete! Copied ${copiedCount} files across ${appDirs.length} apps.`);
}

// Run the script
if (require.main === module) {
  copyFaviconFiles();
}

module.exports = { copyFaviconFiles };
