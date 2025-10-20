# sap-sales-order-portal
A full-stack web application for real-time SAP Sales Order management using Java, JavaScript, and OData V2


# SAP Sales Order Management - Web Portal

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black) ![SAP](https://img.shields.io/badge/SAP-008FD3?style=for-the-badge&logo=sap&logoColor=white)

## 1. Project Overview

This project is a full-stack, standalone web application that serves as a professional user portal for managing SAP Sales Orders in real-time. It communicates directly with a remote SAP Gateway system via a standard **SAP OData v2 service**, performing a complete set of CRUD (Create, Read, Update, Delete) operations in a secure and user-friendly manner.

The architecture follows a modern, decoupled approach, with a pure Java backend acting as a secure proxy and API layer, and a vanilla JavaScript frontend serving as a lightweight, responsive single-page application (SPA).

This prototype was developed to demonstrate a **"Clean Core" extension strategy**, showing how to build modern, targeted user experiences on top of a standard SAP backend without direct system modifications, fully aligning with the modern S/4HANA architecture principles.

### Application Demo

![Application Demo](docs/app-demo.gif)

## 2. Technology Stack

* **Backend:** Java (using the built-in `com.sun.net.httpserver` and `java.net.http.HttpClient`)
* **Frontend:** Vanilla JavaScript (ES6+), HTML5, CSS3
* **API Protocol:** SAP OData v2 (communicating via JSON)
* **Dependencies:** `org.json` for JSON parsing in the Java backend.

## 3. Core Features

* **Read Operations:**
    * List the most recent sales orders for a specific user upon page load.
    * Asynchronously view full header and line item details for any selected order without a page refresh.
* **Create Operations:**
    * Initiate a new sales order from a dedicated modal dialog.
    * Construct a deep insert payload (header + multiple items) and create the order in SAP via a single `POST` request.
* **Update Operations:**
    * Edit specific header fields (e.g., Customer Purchase Order) via a `MERGE` request to ensure only the changed data is transmitted.
* **Delete Operations:**
    * Delete specific line items from a sales order via a `DELETE` request, including a user confirmation step to prevent accidental deletion.

## 4. Key Technical & Security Implementations

This project successfully implements the critical security and concurrency protocols required by SAP OData services for any data modification operation:

* **Authentication:** All requests to SAP are secured using Basic Authentication, managed by the Java backend proxy.
* **CSRF (Cross-Site Request Forgery) Protection:** For all state-changing requests (`POST`, `MERGE`, `DELETE`), the application first performs a preliminary `GET` request with an **`X-CSRF-Token: Fetch`** header to obtain a security token. This token is then included in the subsequent modification request to validate its authenticity.
* **Optimistic Locking (Concurrency Control):** To prevent data overwrites in a multi-user environment, the application captures the **`ETag`** when fetching an entity and includes it in the **`If-Match`** header of all subsequent `MERGE` and `DELETE` requests.
* **Session Management:** The Java `HttpClient` is configured with a `CookieManager` to persist the session cookie (`SAP_SESSIONID`) between the CSRF token fetch and the actual modification request, a critical requirement for the security handshake to succeed.

## 5. Setup and Configuration

To run this project, you will need to configure the connection to your SAP Gateway system.

1.  **Configure Backend:** In the `/backend` directory, rename the `config.properties.example` file to `config.properties`.
2.  **Edit Credentials:** Open the `config.properties` file and enter your SAP system's hostname, username, and password. This file is included in `.gitignore` and will not be committed to the repository.
3.  **Run the Server:** Compile and run the Java backend server.
4.  **Launch Frontend:** Open the `index.html` file from the `/frontend` directory in your web browser.

## 6. Disclaimer

This project is a proof-of-concept and is intended for demonstration and educational purposes only. It is not intended for use in a production environment without further development, including enhanced error handling, logging, and security hardening.
