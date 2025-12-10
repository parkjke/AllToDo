# AllToDo Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    TodoItem {
        Long id PK "Auto-generated ID"
        String text "Todo content"
        Boolean completed "Completion status"
        String source "'local' or 'external'"
        Double latitude "Optional Latitude"
        Double longitude "Optional Longitude"
    }

    LocationEntity {
        Long id PK "Auto-generated ID"
        Double latitude "Latitude"
        Double longitude "Longitude"
        Long timestamp "Recorded Time (ms)"
    }

    %% Relationships can be defined here if any.
    %% Currently, there are no direct foreign keys, but TodoItem can optionally have a location.
```

## Description
*   **TodoItem**: Represents a task. It can optionally have a location (`latitude`, `longitude`) but is not strictly linked to [LocationEntity](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/data/LocationEntity.kt#6-13) by a foreign key.
*   **LocationEntity**: Represents a point in the user's location history track, recorded automatically.
