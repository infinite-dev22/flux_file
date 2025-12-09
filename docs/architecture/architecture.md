# System architecture

This document describes the high-level architecture of the Reactive File Manager application.

Layers:
- Presentation (JavaFX)
- Application (Use Cases)
- Domain (Entities/Value Objects)
- Infrastructure (Adapters/Services)

The system follows a ports-and-adapters (hexagonal) architecture and uses Project Reactor for async/non-blocking operations.
