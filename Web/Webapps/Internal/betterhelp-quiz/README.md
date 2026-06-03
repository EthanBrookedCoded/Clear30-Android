# BetterHelp Quiz

A React + Vite quiz application for the BetterHelp therapy assessment.

## Development

```bash
npm install
npm run dev
```

The app will be available at `http://localhost:5173/betterhelp-quiz/`

## Building for Production

```bash
npm run build
```

This will create a `dist` folder with the production build.

## Deployment

This app is configured to be deployed at the `/betterhelp-quiz/` path on your server.

### Deployment Steps:

1. Build the project:
   ```bash
   npm run build
   ```

2. The `dist` folder contains all the files needed for deployment.

3. Upload the contents of the `dist` folder to your server at the `/betterhelp-quiz/` path.

4. Ensure your web server is configured to:
   - Serve `index.html` for all routes (for client-side routing)
   - Handle the `/betterhelp-quiz/` base path correctly

### Server Configuration Examples:

**Nginx:**
```nginx
location /betterhelp-quiz/ {
    alias /path/to/dist/;
    try_files $uri $uri/ /betterhelp-quiz/index.html;
}
```

**Apache (.htaccess):**
```apache
<IfModule mod_rewrite.c>
  RewriteEngine On
  RewriteBase /betterhelp-quiz/
  RewriteRule ^index\.html$ - [L]
  RewriteCond %{REQUEST_FILENAME} !-f
  RewriteCond %{REQUEST_FILENAME} !-d
  RewriteRule . /betterhelp-quiz/index.html [L]
</IfModule>
```

## Network Access

The dev server is configured to be accessible from other devices on your network. When running `npm run dev`, you'll see both localhost and network URLs in the terminal.
