# Epic 1, 2 & 4: Mobile App (`:mobile`)

  - `MainActivity` with bottom navigation and localized tabs.
  - `CatalogScreen` (Machine catalog management, duplicate name validation, edit machine, Wear OS catalog replication via `DataClient`, localized strings).
  - `TemplateListScreen` (Template management, reordering exercises via Move Up/Down [AK 2.1.3], adding exercises via catalog picker [AK 2.2.1], removing exercises leaving catalog intact [AK 2.2.2], Wear OS templates replication via `DataClient` [US 5.2], localized strings).
  - `HistoryScreen` (Past sessions, set breakdown, localized strings).
  - `MobileTrackingScreen`: Full standalone active workout tracking parity with Wear OS (free workout, template selection, station management, rep/weight input with quick steppers, double progression suggestion banner, rest timer with countdown, ad-hoc machine creation, station skip/replace, template consolidation prompt, Health Connect export, and Wearable sync).
  - `MobileDataLayerListenerService` (Reception via `ChannelClient`, Room insert, Health Connect trigger & ACK, plus bidirectional ACK processing from Wear OS).
