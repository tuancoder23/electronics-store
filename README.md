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

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` in your shell or IDE environment
using `.env.example` as a variable reference. `JWT_SECRET` must contain at least 32 bytes.
Spring Boot does not automatically load a copied `.env` file. Keep local credentials out of Git.
VNPay is disabled by default; see [VNPay sandbox setup](docs/vnpay-sandbox.md) to configure it.

```bash
cd backend
mvn clean test
mvn spring-boot:run
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
- [x] Product catalogue, categories, brands, specifications and images
- [x] Product search, filters, sorting and pagination
- [x] Authentication, JWT authorization and user profile
- [x] Shopping cart, checkout and order management
- [x] Order cancellation, COD and VNPay sandbox payments
- [x] Wishlist and product reviews/ratings
- [x] Database integration (MySQL; H2 for automated tests)
