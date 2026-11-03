# Sign in with Google on Android: the plan

The app is signed out today. The Account tab says so, and the sign-in card is
marked "Coming soon". This file is what has to happen for real sign-in. It is
based on the web app as it stands (`lib/identity/*`, `app/api/auth/*`,
`lib/db/schema.ts`, `lib/sync/account.ts`, `lib/rate-limiter.ts`).

In short: the phone asks Google for an **ID token** through Credential
Manager. A **new endpoint** on lagnaatelier.site checks that token, finds or
creates the `auth_users` row exactly as the web callback does, and returns a
**session token**. The app stores the token encrypted and sends it as a
header. It is the same session row the web uses, so signing out and revoking
work the same way.

Nothing below has been built. The web repo has not been changed.

---

## 1. What you (the owner) create

### 1.1 Two Android OAuth clients in Google Cloud Console

Use the **same Google Cloud project** as the web client (the one whose ID is in
`AUTH_GOOGLE_ID` on Vercel). Then Google counts the phone and the website as
one app, and a person gets the same Google `sub` on both. That matters,
because `auth_identities` is keyed on `sub`.

1. Go to Google Cloud Console → **APIs & Services → Credentials → Create
   credentials → OAuth client ID**.
2. For application type, choose **Android**.
3. Package name: `site.lagnaatelier.app`
4. SHA-1 certificate fingerprint: the **debug** fingerprint (command below).
5. Create it. Then make a **second** Android client with the same package name
   and the **release** fingerprint. If the app goes out through Google Play
   with Play App Signing, add a **third** with the *app signing key* SHA-1
   from Play Console → your app → **App integrity** (under *Test and release*, or *Setup* in older layouts). Play
   re-signs the app, so the upload key's SHA-1 is not the one phones see.
6. Do **not** make a new Web client. The existing web client ID is the one the
   app passes to Google as `serverClientId` (see 2.2). The Android clients have
   no secret and are not entered anywhere in code. They exist so that Google
   trusts tokens it hands to an app with that package name and signature.
7. The consent screen is shared. While the project is in **Testing**, only
   listed test users can sign in on the phone too. The web callback reports
   that case as `declined` (see the note in `app/api/auth/google/callback`).

#### Printing the debug SHA-1

Android Studio's bundled JDK has `keytool`. From PowerShell:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

Copy the `SHA1:` line. You can also run this from the Android project:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat signingReport
```

It prints the SHA-1 for every variant. Today the `release` variant prints no
fingerprint, because `app/build.gradle.kts` has no release signing config yet.
That has to be set up (a release keystore kept out of git) before step 5 of
1.1 can be done. Each machine has its own debug
keystore, so a second developer's debug build needs its own fingerprint added
to the debug client, or that person shares the keystore file.

For the release fingerprint, run the same `keytool -list -v` command against
the release keystore and its alias. Keep that keystore and its passwords out of
the repo.

### 1.2 Two new Vercel environment variables

| Variable | Value |
|---|---|
| `AUTH_GOOGLE_ANDROID_IDS` | The Android client IDs from 1.1, comma-separated (debug, release, Play signing). The server checks the token's `azp` against this list. |
| (existing) `AUTH_GOOGLE_ID` | Unchanged. It is the **audience** the server requires for Android tokens too. |

No new secret is needed. An ID token is checked against Google's public keys,
not against a client secret.

### 1.3 Decisions only you can make

- **New dependencies in the app**: `androidx.credentials:credentials`,
  `androidx.credentials:credentials-play-services-auth` and
  `com.google.android.libraries.identity.googleid:googleid`. These are
  Google's supported way to do this. The older `play-services-auth`
  `GoogleSignInClient` is deprecated, so do not use it. Sign-in will need Google
  Play services on the phone. On a phone without them, the card keeps saying
  sign-in is not available.
- **Saved charts and consent**: on the web, signing in does not upload
  anything. Charts go to the account only after an explicit consent
  (`lib/sync/consent.ts`, `CONSENT_POLICY_VERSION`). The app must ask the same
  question before it sends the five charts kept on the phone, and record it the
  same way. Signing in alone moves nothing.

---

## 2. The Android side

### 2.1 Flow

```
Account tab "Sign in with Google"
  → POST /api/auth/google/android/nonce          (server issues a one-use nonce)
  → CredentialManager.getCredential(GetGoogleIdOption(serverClientId = WEB client ID, nonce))
  → GoogleIdTokenCredential.createFrom(credential.data).idToken
  → POST /api/auth/google/android { idToken, nonce }
  ← { token, expiresAt, user: { email, displayName } }
  → store token encrypted; send "Authorization: Bearer <token>" on every call
```

### 2.2 Details

- Build the request with `GetGoogleIdOption.Builder()`:
  - `setServerClientId(<the WEB client ID>)`. This is the value of
    `AUTH_GOOGLE_ID`, not an Android client ID. Google then mints the token
    with `aud` set to the web client and `azp` set to the Android client. Put
    it in the build as a `buildConfigField` or a non-translatable string. It
    is public and not a secret.
  - `setNonce(nonce)` with the nonce from the server.
  - First try with `setFilterByAuthorizedAccounts(true)` and
    `setAutoSelectEnabled(true)`. That signs a returning person straight in.
    If it fails with `NoCredentialException`, try again with
    `setFilterByAuthorizedAccounts(false)` so the account chooser opens.
  - For the explicit button, `GetSignInWithGoogleOption` gives Google's
    branded sheet. Either option returns the same ID token.
- Make the call from a coroutine in `AppViewModel`, never from a
  composable. It needs an Activity `Context` for the bottom sheet.
- If the result is not a `GoogleIdTokenCredential`
  (`TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`), refuse it. Catch
  `GetCredentialCancellationException` (the person closed the sheet: no
  message) separately from other errors ("Sign-in did not go through. Try
  again.").
- Never log the ID token or the session token. Never put either in a URL.

### 2.3 Keeping the session token

- Store the token encrypted with a key held in the **Android Keystore**.
  `EncryptedSharedPreferences` (`androidx.security:security-crypto`) does this,
  but Google has deprecated it. Prefer an AES-GCM key you generate yourself in
  the Keystore (`KeyGenParameterSpec`, `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`,
  no user-auth requirement). Use it to encrypt the token, and keep the
  ciphertext and IV in their own private SharedPreferences file. Tink with an
  Android Keystore master key is another option. Each of these adds a
  dependency, except the hand-rolled Keystore one.
- Leave that file out of backup and device transfer. Add an `<exclude>` for it
  in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
  Keystore keys are not backed up, so a restored copy could not be decrypted
  anyway. If decryption fails, treat it as signed out and delete the file.
- Keep only what the screen needs (display name, email) next to it. Do not
  keep the Google ID token after the exchange. It has done its job.

### 2.4 Using it

- `LagnaApi` adds `Authorization: Bearer <token>` when a token is stored. With
  that header, the signed-in limits apply to the app (20 readings per route per
  day instead of 10, `lib/llm-budget-tiers.ts`).
- If any response is `401` with the header present, the session has expired
  or was revoked. Delete the token, show "Not signed in" and keep everything
  else working. Charts work signed out.
- Sign out: `POST /api/auth/signout` with the header, then delete the token
  whatever the response, then call `CredentialManager.clearCredentialState()`
  so the next sign-in does not pick the same account on its own.

---

## 3. The server side (does not exist yet)

### 3.1 `POST /api/auth/google/android/nonce`

- Returns `{ nonce }`: 32 random bytes in base64url, the same way
  `startHandshake` makes its nonce.
- Store only `sha256(nonce)` with `expires_at = now + 10 min`
  (`OAUTH_HANDSHAKE_MAX_AGE`) in a new table, for example
  `auth_nonces (nonce_hash text primary key, expires_at timestamptz,
  created_at timestamptz)`. That needs a migration. Cookies cannot carry it,
  because the app is not a browser.
- Rate-limit by `clientKey(request)` (the IPv6 /64 rule), for example 10 per
  minute, with a new entry in `ROUTE_LIMITS`.
- `Cache-Control: no-store, private`.

### 3.2 `POST /api/auth/google/android`

Body: `{ idToken: string, nonce: string }`. Check it with zod, like the other
routes, and cap the size (an ID token is about 1–2 KB).

1. **Rate limit** by `clientKey`, for example 10 per minute, with its own
   `ROUTE_LIMITS` entry. A 429 says nothing about why.
2. **Verify the token** with `jose`, reusing the `jwks` already in
   `lib/identity/google-oauth.ts`. Move the `jwtVerify` block out of
   `exchangeCode` into an exported `verifyGoogleIdToken(idToken, { audience,
   expectedNonce, authorizedParties })`, so the web and the app run one
   check:
   - `algorithms: ["RS256"]`, `issuer: ["https://accounts.google.com",
     "accounts.google.com"]`, `clockTolerance: 60`. These are the same settings
     as today.
   - **Audience pinning**: `audience: AUTH_GOOGLE_ID`, the web client, and
     nothing else. A token minted for any other app's client ID must fail, even
     though Google signed it. This is the check that stops someone from
     replaying a token they got from their own app.
   - **Authorised party**: `claims.azp` must be one of
     `AUTH_GOOGLE_ANDROID_IDS`. This proves the token was handed to *this*
     package with *this* signature.
   - **Nonce**: `claims.nonce` must equal the posted nonce. Then consume the
     nonce in one statement (`DELETE FROM auth_nonces WHERE nonce_hash = $1
     AND expires_at > now() RETURNING 1`). If no row comes back, the nonce is
     unknown, expired or already used, and the request fails. A token cannot
     be replayed, even inside its one-hour life.
   - `sub` and `email` must both be present.
3. **`email_verified` must be `true`**, or the request fails with
   `email_unverified`. This is the same rule as the web callback, and
   `signInWithProvider` depends on it when it links by email.
4. **Link the account** with the existing
   `signInWithProvider({ provider: "google", subject, email, emailVerified,
   name })` from `lib/identity/link-account.ts`. Do not copy it. It keys on
   `sub`, links to an existing `auth_users` row by verified email only, and
   creates the user otherwise. Someone who signed in on the website and then on
   the phone gets the same `userId`. Log `account_created` as the callback does.
5. **Start a session** with the existing `createSession(userId, { userAgent,
   ipAddress: getClientIp(request) })`. It is the same `auth_sessions` row,
   storing only the SHA-256 of the token, with a 30-day sliding expiry.
6. **Respond** `200 { token, expiresAt, user: { email, displayName } }` with
   `Cache-Control: no-store, private`. **Set no cookie.** Any failure gets a
   coarse code (`invalid_token`, `email_unverified`, `signin_failed`) and the
   detail goes to the server log via the `logAuthFailure` pattern. The caller
   is not told which check failed.

### 3.3 Accepting the header everywhere a session is read

Every reader looks only at the `astro_session` cookie today:
`lib/identity/require-session.ts` (used by `lib/llm-budget.ts` and
`advanced-story`), `lib/sync/account.ts` (`findRequestAccount`),
`app/api/auth/session/route.ts` and `app/api/auth/signout/route.ts`.

- Add one `sessionTokenFromRequest(request)` in `require-session.ts`. It
  returns the `Authorization: Bearer` token if one is there, or the cookie if
  not. Route all four readers through it, so that a route cannot accept the
  header while its neighbour ignores it.
- A bearer header is never sent by a browser on its own, so it brings no new
  CSRF exposure. The cookie path keeps its existing protections.
- `signout` revokes the bearer token when one is present, and still clears the
  cookie.

### 3.4 Revocation and limits

- **Signing out** revokes the row (`revokeSession`), and every later request
  with that token is refused at once. That is why the token is an opaque random
  value looked up per request, not a JWT (see the comment in `session.ts`).
- **"Sign out everywhere"** is `revokeAllSessions(userId)`, which already
  exists. It ends the phone's and the web's sessions together.
- **Disabled or deleted accounts**: `resolveSession` already refuses
  `disabledAt` users. The `auth_users` → `auth_sessions` foreign key cascades
  on delete.
- **Google-side removal**: if someone removes the app from their Google
  account, nothing tells the server today, and the session runs until it
  expires or they sign out. Google's Cross-Account Protection (RISC) events can
  close that gap later. It is not needed to launch.
- **Lost phone**: the person signs out everywhere from the website. A
  per-session list on `/login` would be nicer, since `auth_sessions` already
  stores user agent and IP, but that is optional.

### 3.5 Tests to add on the web side

Write these next to `app/api/auth/__tests__`. Each case should fail with a
token that is wrong in exactly one way:

- wrong `aud`, wrong `azp`, wrong `iss`, expired, `alg` other than RS256, bad
  signature
- missing nonce, wrong nonce, reused nonce, expired nonce
- `email_verified: false`
- an existing web user is linked by `sub`, a verified-email user is linked by
  email, a new user is created
- a bearer token resolves in `sessionFromRequest`, `findRequestAccount` and
  `/api/auth/session`
- a revoked bearer token is refused
- `signout` with a bearer token revokes the token
- the rate limit returns 429

---

## 4. Order of work

1. Owner: the Android clients and `AUTH_GOOGLE_ANDROID_IDS` (section 1).
2. Web: the nonce table migration, `verifyGoogleIdToken` refactor, the two
   endpoints, the bearer reader, the tests. Deploy.
3. App: the dependencies (with your OK), the Credential Manager call, token
   storage, the header in `LagnaApi`, the 401 handling. Then the Account card
   becomes a real button, and the "Coming soon" tag comes off.
4. Later, and only with the consent screen: offer to send the phone's saved
   charts to the account.
