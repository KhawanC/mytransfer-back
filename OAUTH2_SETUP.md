# Google OAuth2 Configuration Guide

This guide explains how to set up Google OAuth2 authentication for the MyTransfer application.

## Prerequisites

- A Google account
- Access to [Google Cloud Console](https://console.cloud.google.com/)

## Step 1: Create a Google Cloud Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Click on the project dropdown at the top of the page
3. Click **New Project**
4. Enter a project name (e.g., "MyTransfer")
5. Click **Create**

## Step 2: Configure OAuth Consent Screen

1. In the Google Cloud Console, navigate to **APIs & Services** > **OAuth consent screen**
2. Select **External** user type (unless you have a Google Workspace organization)
3. Click **Create**
4. Fill in the required fields:
   - **App name**: MyTransfer
   - **User support email**: Your email address
   - **Developer contact information**: Your email address
5. Click **Save and Continue**

### Scopes Configuration

1. Click **Add or Remove Scopes**
2. Select the following scopes:
   - `email` - View your email address
   - `profile` - View your basic profile info
   - `openid` - Associate you with your personal info
3. Click **Update**
4. Click **Save and Continue**

### Test Users (Development Only)

1. Add your email addresses as test users
2. Click **Save and Continue**

## Step 3: Create OAuth 2.0 Credentials

1. Navigate to **APIs & Services** > **Credentials**
2. Click **+ Create Credentials** > **OAuth client ID**
3. Select **Web application** as the application type
4. Configure the following:

   **Name**: MyTransfer Web Client

   **Authorized JavaScript origins**:
   ```
   http://localhost:3000
   http://localhost:8080
   ```

   **Authorized redirect URIs**:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```

5. Click **Create**
6. Copy the **Client ID** and **Client Secret**

## Step 4: Configure Environment Variables

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Update the `.env` file with your credentials:
   ```properties
   GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-client-secret
   ```

## Step 5: Generate JWT Secret

Generate a secure JWT secret key (minimum 32 characters):

```bash
# Using OpenSSL
openssl rand -base64 32

# Or using Python
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

Update your `.env` file:
```properties
JWT_SECRET=your-generated-secret-key
```

## Production Configuration

For production deployment, update the following:

### 1. OAuth Consent Screen
- Submit your app for verification if you want to allow any Google user (not just test users)
- Add your privacy policy and terms of service URLs

### 2. OAuth Credentials
Update the authorized origins and redirect URIs:
```
Authorized JavaScript origins:
https://your-domain.com

Authorized redirect URIs:
https://your-api-domain.com/login/oauth2/code/google
```

### 3. Environment Variables
Update the `.env` file for production:
```properties
APP_BASE_URL=https://your-api-domain.com
APP_FRONTEND_URL=https://your-domain.com
```

## Testing OAuth2 Flow

### 1. Start the Application
```bash
./mvnw spring-boot:run
```

### 2. Initiate OAuth2 Login
Navigate to:
```
http://localhost:8080/oauth2/authorization/google
```

### 3. Complete Authentication
- You will be redirected to Google's login page
- After successful authentication, you'll be redirected to:
  ```
  http://localhost:3000/oauth/callback?access_token=...&refresh_token=...&expires_in=...
  ```

### 4. Handle Tokens in Frontend
Your frontend should:
1. Extract tokens from URL parameters
2. Store them securely (preferably in httpOnly cookies or secure storage)
3. Include the access token in subsequent API requests

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Register with email/password |
| `/api/auth/login` | POST | Login with email/password |
| `/api/auth/refresh` | POST | Refresh access token |
| `/api/auth/me` | GET | Get current user info |
| `/oauth2/authorization/google` | GET | Initiate Google OAuth2 |

## Request Examples

### Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "securePassword123"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "securePassword123"
  }'
```

### Refresh Token
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "your-refresh-token"
  }'
```

### Get Current User
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer your-access-token"
```

## Troubleshooting

### "redirect_uri_mismatch" Error
- Ensure the redirect URI in Google Cloud Console exactly matches:
  `http://localhost:8080/login/oauth2/code/google`
- Check for trailing slashes

### "Access Denied" Error
- Ensure your email is added as a test user (during development)
- Check if the OAuth consent screen is properly configured

### Token Validation Errors
- Verify the JWT secret is at least 32 characters
- Check token expiration times in `.env`

## Security Best Practices

1. **Never commit `.env` files** - They contain sensitive credentials
2. **Use HTTPS in production** - OAuth2 requires secure connections
3. **Rotate secrets regularly** - Update JWT secret and OAuth credentials periodically
4. **Limit token expiration** - Access tokens: 15 min, Refresh tokens: 7 days
5. **Validate redirect URIs** - Prevent open redirect vulnerabilities
