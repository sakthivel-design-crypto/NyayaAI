# NyayaaAI Cloud Functions Backend: Password Reset & Verification

This directory contains the production Firebase Cloud Functions implementing the **Citizen Password Reset with 6-Digit OTP Verification** system for the project `nyayaai-e2f86`.

## Architecture & Security Specifications

1. **Cryptographically Secure OTP**:
   - Random 6-digit OTP generated exclusively on the server (`crypto.randomInt(100000, 1000000)`).
   - 10-minute expiration window.
   - Max 5 incorrect attempts before automatic invalidation.
   - 60-second rate-limiting between resends.
   - OTPs are stored exclusively as salted SHA-256 hashes in `_auth_password_resets` in Firestore (secured by security rules against client reads).
   - Plaintext OTPs are never stored or logged.

2. **Transactional Email Delivery**:
   - Dispatches email via Nodemailer (Resend / SendGrid / SMTP / Webhook).
   - Subject: `NyayaaAI Password Reset Verification Code`
   - Body format:
     - "Your NyayaaAI password reset verification code is: [6-digit code]"
     - "Your verification code expires in 10 minutes."
     - "Do not share this code with anyone."

3. **Single-Use Reset Authorization**:
   - Successful OTP verification produces a high-entropy 256-bit single-use `resetToken` valid for 15 minutes.
   - The password reset endpoint requires this token to update the real Firebase Authentication user password via the Firebase Admin SDK (`admin.auth().updateUser(uid, { password })`).
   - Refresh tokens are revoked after update.

4. **Google-Only Account Guard**:
   - Detects users registered exclusively via Google Sign-In and prevents password reset OTPs, informing the user:
     *"This account uses Google Sign-In. Please use Continue with Google to access your account."*

---

## Deployment Instructions

### Prerequisites
- Node.js 18+
- Firebase CLI installed (`npm install -g firebase-tools`)
- Logged in to Firebase (`firebase login`)

### Step 1: Install Dependencies
```bash
cd functions
npm install
```

### Step 2: Configure Email Provider & Secrets (Optional / Recommended)
Set your transactional email provider in the Firebase environment:

**Using Resend (Recommended):**
```bash
firebase functions:config:set resend.key="re_your_api_key"
```

**Using SendGrid:**
```bash
firebase functions:config:set sendgrid.key="SG.your_api_key"
```

**Using Standard SMTP (e.g. Gmail / Amazon SES):**
```bash
firebase functions:config:set smtp.url="smtps://user%40gmail.com:app-password@smtp.gmail.com:465"
```

### Step 3: Deploy Cloud Functions to Firebase
```bash
firebase deploy --only functions
```

### Endpoints Generated
- `sendPasswordResetOtp`: `https://us-central1-nyayaai-e2f86.cloudfunctions.net/sendPasswordResetOtp`
- `verifyPasswordResetOtp`: `https://us-central1-nyayaai-e2f86.cloudfunctions.net/verifyPasswordResetOtp`
- `executePasswordReset`: `https://us-central1-nyayaai-e2f86.cloudfunctions.net/executePasswordReset`
