# Migration GUI Farm / Farm UI migration

HomeLink Farm 1.5.0 utilise HomeCore 1.14.0, API publique 1.9.0. Les implémentations locales `FarmTheme` et `FarmButton` sont supprimées.

| Ancien code / Previous code | API actuelle / Current API |
| --- | --- |
| palette `FarmTheme` | `HomeLinkTheme` |
| `window`, `panel`, `slot`, `screw`, `gauge` | mêmes helpers `HomeLinkUi` |
| `divider`, `statusLight` | `HomeLinkUi.separator`, `statusDot` |
| `pumpStatus`, `farmBotStatus` | `FarmStatusColors`, mapping métier local vers `HomeLinkStatusTone` |
| `FarmButton.builder` | `HomeLinkButton.builder` |
| `accentWhen(supplier)` | `HomeLinkButton.selectedWhen(supplier)` ; fond gris enfoncé, texte cuivre |
| champ de renommage | `HomeLinkUi.input(...)`, hauteur 18 |

Le header utilise 29 pixels et les contrôles standard 18. Les panneaux, informations et coordonnées des slots restent identiques. Les contrôles des lignes compactes de diagnostic gardent leur hauteur fonctionnelle. Les fenêtres existantes restent larges de 270 pixels, avec une hauteur maximale de 236 ; prévoir 286 × 252 pixels GUI pour conserver toutes les marges. Sous ces dimensions, réduire l'échelle GUI.

Les quatre écrans migrés sont `FarmControllerScreen`, `CropMonitorScreen`, `IrrigationPumpScreen` et `FarmBotStationScreen`, via le shell consommateur `FarmDeviceScreen`. Les aides `FarmHelpView` utilisent les panneaux et couleurs HomeCore. Le shell Farm garde ses états, commandes et logique propres ; HomeCore n'impose aucune classe écran de base.

**English:** only client screens/renderers may import `fr.lkdm.homecore.api.client.ui`. Domain logic, device status mapping, networking, irrigation overlays, Crop Monitor zones and FarmBot behavior remain in Farm. The 3D overlay palette is unchanged. New HomeLink consumers depend directly on HomeCore, without installing Dashboard or copying any theme. Keep translated labels, full-label tooltips, native narration, keyboard traversal and visible focus.

Use the explicit dependency `fr.lkdm.homecore:homecore:1.14.0` and metadata `[1.14.0,2.0.0)`. Local composites require the matching HomeCore checkout; they neither publish a package nor automatically update sources from GitHub.

HomeCore 1.14.0 has not been published to Maven as part of this migration.
Build with matching adjacent HomeCore and Energy sources and
`-PuseLocalDependencies=true`; pushing Git commits does not publish a Maven artifact.

La CI clone le consommateur, HomeCore et Energy dans des dossiers voisins et
utilise ces composites, avec des commits de dépendances épinglés dans
`.github/workflows/ci.yml`. **EN:** private dependency repositories require
`HOMELINK_REPOSITORIES_TOKEN` with Contents read access to HomeCore and Energy.
An existing `HOMELINK_PACKAGES_TOKEN` with the same repository access is also
accepted; public repositories can use the `github.token` fallback. Tokens are
not persisted by checkout, and these workflows do not publish Maven artifacts.
