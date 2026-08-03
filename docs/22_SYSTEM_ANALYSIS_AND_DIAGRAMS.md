# System Analysis and Diagrams

This folder contains the editable analysis and design artifacts for the Parking Management System.

## Artifacts

- [Existing Systems and Business Processes](parking_existing_systems_and_business_process.docx)  
  Word report covering existing-system research, business processes, entity descriptions, relationship rules, and the current `IncidentImage` implementation note.

- [Complete Entity Relationship Diagram](parking_system_erd.drawio)  
  Editable one-page ERD containing all backend entities and crow's-foot relationships.

- [Business Process Swimlanes](parking_business_process_swimlanes.drawio)  
  Editable swimlane diagrams for booking, check-in/check-out, payment, monthly tickets, incidents, and administration.

- [System State Diagrams](parking_system_state_diagrams.drawio)  
  Editable state diagrams for booking, parking sessions, payments, monthly-ticket requests and tickets, incidents, and parking cards. Dashed states or transitions identify enum values that are declared but are not currently assigned by backend services.

Open `.drawio` files with [diagrams.net](https://app.diagrams.net/) or the draw.io desktop application.

## IncidentImage implementation status

`IncidentImage` is a real backend entity mapped to the `incident_images` table. `IncidentReport` has a one-to-many relationship with it, and `IncidentReportService` can persist image metadata supplied through `CreateIncidentRequest.images`.

The feature is currently only partially implemented:

1. The current frontend incident form does not select, upload, or submit incident images.
2. The backend does not provide an incident-specific multipart upload endpoint.
3. The existing `VehicleImage` upload endpoint is used for parking-session entry and exit images and does not create `IncidentImage` records.

Keep `IncidentImage` in the ERD because it is part of the backend database model. Treat incident evidence upload as incomplete until the frontend form, Cloudinary upload endpoint, permission checks, cleanup behavior, and tests are added.

## Source-of-truth guidance

- Use the Java classes under `src/main/java/Parking/Model` as the source of truth for entities and persistence relationships.
- Use controllers and services as the source of truth for implemented workflows.
- Update both the Word report and the relevant Draw.io file whenever an entity, relationship, or business process changes.
