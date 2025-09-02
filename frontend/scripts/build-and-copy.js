#!/usr/bin/env node

/**
 * Build and Copy Script for Chess React UI
 * 
 * This script:
 * 1. Builds the React application
 * 2. Copies the build files to the backend static directory
 * 3. Ensures proper dual UI support
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const FRONTEND_DIR = __dirname;
const BACKEND_DIR = path.join(FRONTEND_DIR, '..');
const BUILD_DIR = path.join(FRONTEND_DIR, 'dist');
const TARGET_DIR = path.join(BACKEND_DIR, 'src', 'main', 'resources', 'static', 'newage', 'chess');

console.log('🚀 Starting Chess React UI Build and Copy Process...');

try {
  // Step 1: Clean previous build
  console.log('🧹 Cleaning previous build...');
  if (fs.existsSync(BUILD_DIR)) {
    fs.rmSync(BUILD_DIR, { recursive: true, force: true });
  }
  if (fs.existsSync(TARGET_DIR)) {
    fs.rmSync(TARGET_DIR, { recursive: true, force: true });
  }

  // Step 2: Build React application
  console.log('🔨 Building React application...');
  execSync('npm run build', { 
    cwd: FRONTEND_DIR, 
    stdio: 'inherit' 
  });

  // Step 3: Verify build
  if (!fs.existsSync(BUILD_DIR)) {
    throw new Error('Build directory not found. Build may have failed.');
  }

  // Step 4: Create target directory structure
  console.log('📁 Creating target directory structure...');
  fs.mkdirSync(TARGET_DIR, { recursive: true });

  // Step 5: Copy build files
  console.log('📋 Copying build files...');
  copyDirectory(BUILD_DIR, TARGET_DIR);

  // Step 6: Update index.html for proper routing
  console.log('🔧 Updating index.html for dual UI support...');
  updateIndexHtml();

  // Step 7: Create .htaccess for SPA routing (if needed)
  console.log('⚙️ Creating SPA routing configuration...');
  createSPARoutingConfig();

  // Step 8: Verify copy
  console.log('✅ Verifying copy...');
  verifyCopy();

  console.log('🎉 Build and copy completed successfully!');
  console.log(`📂 React app available at: ${TARGET_DIR}`);
  console.log('🌐 New UI will be accessible at: http://localhost:8081/newage/chess/');
  console.log('🔄 Existing UI remains at: http://localhost:8081/');

} catch (error) {
  console.error('❌ Build and copy failed:', error.message);
  process.exit(1);
}

function copyDirectory(src, dest) {
  const entries = fs.readdirSync(src, { withFileTypes: true });
  
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    
    if (entry.isDirectory()) {
      fs.mkdirSync(destPath, { recursive: true });
      copyDirectory(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

function updateIndexHtml() {
  const indexPath = path.join(TARGET_DIR, 'index.html');
  
  if (fs.existsSync(indexPath)) {
    let content = fs.readFileSync(indexPath, 'utf8');
    
    // Add base tag for proper asset loading
    if (!content.includes('<base href="/newage/chess/">')) {
      content = content.replace(
        '<head>',
        '<head>\n  <base href="/newage/chess/">'
      );
    }
    
    // Add meta tags for PWA
    const metaTags = `
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="theme-color" content="#1e293b">
  <meta name="description" content="Advanced Chess Game with 12 AI Systems">
  <link rel="manifest" href="/newage/chess/manifest.json">
  <link rel="icon" type="image/png" href="/newage/chess/pwa-192x192.png">`;
    
    if (!content.includes('theme-color')) {
      content = content.replace('</head>', `${metaTags}\n</head>`);
    }
    
    fs.writeFileSync(indexPath, content);
  }
}

function createSPARoutingConfig() {
  // Create a simple routing configuration file
  const routingConfig = `# SPA Routing Configuration for Chess React UI
# This file helps with client-side routing in the React application

# Enable rewrite engine
RewriteEngine On

# Handle React Router routes
RewriteCond %{REQUEST_FILENAME} !-f
RewriteCond %{REQUEST_FILENAME} !-d
RewriteRule ^(.*)$ /newage/chess/index.html [L]

# Cache static assets
<FilesMatch "\\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$">
  ExpiresActive On
  ExpiresDefault "access plus 1 year"
</FilesMatch>

# Security headers
Header always set X-Content-Type-Options nosniff
Header always set X-Frame-Options DENY
Header always set X-XSS-Protection "1; mode=block"
`;

  const configPath = path.join(TARGET_DIR, '.htaccess');
  fs.writeFileSync(configPath, routingConfig);
}

function verifyCopy() {
  const requiredFiles = [
    'index.html',
    'manifest.json',
    'pwa-192x192.png',
    'pwa-512x512.png'
  ];
  
  for (const file of requiredFiles) {
    const filePath = path.join(TARGET_DIR, file);
    if (!fs.existsSync(filePath)) {
      throw new Error(`Required file not found: ${file}`);
    }
  }
  
  // Check for assets directory
  const assetsPath = path.join(TARGET_DIR, 'assets');
  if (!fs.existsSync(assetsPath)) {
    console.warn('⚠️ Assets directory not found. This may be normal for some builds.');
  }
  
  console.log('✅ All required files copied successfully');
}
