# Electronics Store 🛒

> A modern full-stack e-commerce platform for electronics, built with **Spring Boot 3** (backend) and **React + Vite** (frontend).

---

## Tech Stack

| Layer    | Technology                                    |
|----------|-----------------------------------------------|
| Backend  | Java 21, Spring Boot 3.4, Maven               |
| Frontend | React 19, TypeScript, Vite 6                  |
| Database | MySQL                        |

---

## Project Structure

```
electronics-store/
├── backend/          # Spring Boot REST API
│   ├── src/
│   │   ├── main/java/com/electronics/store/
│   │   │   ├── config/        # CORS, web config
│   │   │   ├── controller/    # REST controllers
│   │   │   ├── dto/           # Request / Response DTOs
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── entity/        # JPA entities 
│   │   │   ├── exception/     # Global error handling
│   │   │   ├── mapper/        # DTO ↔ Entity mappers 
│   │   │   ├── repository/    # Spring Data repos 
│   │   │   └── service/       # Business logic 
│   │   └── resources/
│   │       └── application.yml
│   └── pom.xml
├── frontend/         # React + Vite SPA
│   ├── src/
│   ├── index.html
│   └── package.json
├── docs/             # Project documentation
├── .gitignore
└── README.md
```

---

## Getting Started

### Prerequisites

- Java 21+
- Node.js 20+
- Maven 3.9+

### Backend

```bash
cd backend
./mvnw spring-boot:run
# API available at http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev
# Dev server at http://localhost:5173
```

---

## API Endpoints

| Method | Endpoint      | Description        |
|--------|---------------|--------------------|
| GET    | `/api/health` | Health check       |

---

## Current Status

- [x] Project scaffold (backend + frontend)
- [x] `GET /api/health` endpoint
- [x] CORS configuration
- [x] Global exception handler
- [ ] Product catalogue API
- [ ] Category API
- [ ] Shopping cart
- [ ] Authentication & Authorization
- [ ] Database integration (PostgreSQL)
