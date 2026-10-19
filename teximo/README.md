# Teximo — AI-Powered Career & Placement Management System

Frontend: HTML, CSS, JavaScript, Bootstrap 5  
Backend: Java 21, Spring Boot 3, Spring Data JPA, MySQL 8  
AI: OpenRouter · Jobs: RapidAPI JSearch

```
teximo/
├── frontend/
│   ├── index.html      Student Login / Admin Login / Student registration
│   ├── student.html    Student UI (only STUDENT accounts can open it)
│   ├── admin.html      Admin UI   (only ADMIN accounts can open it)
│   ├── css/style.css   Theme + personal assistant widget
│   └── js/             config.js, api.js, login.js, student.js, admin.js, chatbot.js
└── backend/            Spring Boot REST API
```

## Run locally

1. **MySQL 8** running locally (the database `teximo` is created automatically).
2. **Backend**
   ```bash
   cd backend
   export DB_USERNAME=root
   export DB_PASSWORD=your_mysql_password
   export TEXIMO_MASTER_KEY="a-long-random-string"     # encrypts saved API keys
   export TEXIMO_TOKEN_SECRET="another-long-random-string"
   export ADMIN_EMAIL=admin@123
   export ADMIN_PASSWORD="Pass@123"
   export RAPIDAPI_KEY="your-rapidapi-key"
   export OPENROUTER_KEY="your-openrouter-key"
   mvn spring-boot:run
   ```
   The configured admin account is synchronized on backend startup. Runs on http://localhost:8080.
3. **Frontend**: open `frontend/index.html` through any static server, e.g.
   ```bash
   cd frontend && python3 -m http.server 5500
   ```
   then visit http://localhost:5500. `frontend/js/config.js` uses `http://localhost:8080/api` for local development.
4. Sign in as Admin, open **API & Database**, paste the shared RapidAPI key and/or OpenRouter key, and press **Save securely**. Keys stay masked and are never returned to the website.

Students register on the **Student Login** tab → "Register here". Admin accounts cannot be created from the website.

- Admin can download CSV files from Students, Companies, Placement Drives, Applications, and Interviews.
- Changing an application to `SHORTLISTED` creates one online Round 1 interview seven days later at 10:00 AM Asia/Kolkata; Admin can manage interviews from the Interviews tab.
- Generated resumes can be downloaded as PDF from Resume Studio.
- The Admin Companies tab shows a separate live list of employers found in JSearch job results; external company records are not saved locally. JSearch results are restricted to India (`country=IN`). A JSearch 404 means the RapidAPI subscription or endpoint needs checking.

## How the two UIs are separated

- Login has two tabs. The account's real role must match the tab used.
- Backend endpoints are guarded by `@RequireRole(STUDENT)` or `@RequireRole(ADMIN)` (`/api/student`, `/api/jobs`, `/api/resume`, `/api/chat` = students; `/api/admin/**` = admins).
- `student.html` and `admin.html` bounce visitors to login if the role is wrong.

## Secrets handling

- Provider keys can be supplied through backend environment variables or saved in Admin → API & Database; saved keys are AES-256-GCM encrypted (`data/secrets.enc`, key derived from `TEXIMO_MASTER_KEY`).
- `GET /api/admin/config` returns only "Saved / Not set" flags. Real values are never returned to the frontend.
- Admin → API & Database has separate encrypted RapidAPI key fields for job search, job salary, company job search, and job details, plus a JSearch endpoint URL field copied from the RapidAPI Job Search code snippet. Paste only the HTTPS request URL (not the cURL command or key); the backend derives the `X-RapidAPI-Host` header from that URL. The JSearch call uses its dedicated key and falls back to legacy `RAPIDAPI_KEY`. Salary and job-details keys are stored securely for later integration.
- Database credentials entered in the settings page are encrypted and tested, but changes apply after restarting the backend. Never put API keys in frontend files.
- Always serve both sites over HTTPS in production and set `TEXIMO_ALLOWED_ORIGIN` to your Vercel URL.

## Rules the platform follows

- Job results from JSearch are read-only. The student opens the original posting and applies by themselves.
- Teximo never auto-applies and never uploads resumes or messages to LinkedIn, Naukri or any outside site.
- Duplicate listings (same title + company + city) are removed.

## Personal assistant

The student page includes a personal assistant for resume, interview, placement, and job questions. Its greeting uses the signed-in student's name, and responses are handled by the backend.

## Deploy

- **Frontend → Vercel:** Import the project with the repository root as the Vercel project root. The included `vercel.json` runs `npm run build` and publishes `dist/`. In Vercel Project Settings → Environment Variables, set `TEXIMO_API` to the HTTPS URL of your separately deployed backend, ending in `/api` (for example, `https://your-backend.example.com/api`). The build inserts this URL into the generated frontend; do not put provider keys here. Redeploy after setting or changing the variable.
- **Backend and database:** Vercel serves this static frontend; it does not run the Spring Boot application or MySQL database from this configuration. Deploy the backend to a Java host and use a production MySQL-compatible database. Set `TEXIMO_ALLOWED_ORIGIN` on the backend to your Vercel production URL (and any preview URL you intend to use), and configure the database, token-signing, encryption, admin, and provider-key environment variables there.
- Resume upload limit is 500 MB (`application.properties`). Your host and proxy may impose a lower limit.
