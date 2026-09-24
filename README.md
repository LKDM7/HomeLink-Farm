# HomeLink Farm 1.0.0

Module agricole de l'écosystème HomeLink : surveiller, diagnostiquer, irriguer, optimiser et connecter une exploitation Minecraft, sans jamais jouer à la place du joueur (pas de plantation, de récolte ni de création de ressources).

**Minecraft 1.21.1 · NeoForge 21.1.250+ · Java 21 · HomeCore 1.3.0+ (obligatoire).**

```text
Farm → Crop Monitor → Farm Controller → HomeCore        Water → Irrigation Pump → Copper Pipes → max 5 Sprinklers → +20 % de croissance
```

## Installation

1. Installer **HomeCore 1.3.0** ([LKDM7/HomeCore](https://github.com/LKDM7/HomeCore)) côté client et serveur.
2. Installer `homelink_farm-1.0.0.jar` dans `mods` côté client et serveur.

Compilation depuis les sources (JDK 21) : publier d'abord HomeCore dans le Maven local (`./gradlew.bat publishToMavenLocal` dans le dépôt HomeCore), puis :

```powershell
./gradlew.bat build          # JAR dans build/libs + tests JUnit
```

## Blocs et objets

| Élément | Rôle |
| --- | --- |
| **Farm Controller** | Cerveau de l'exploitation : UUID stable, nom, propriétaire, composants reliés, statistiques agrégées, appareil HomeCore. |
| **Crop Monitor** | Surveille une zone : cultures, prêtes / en croissance, maturité, irrigation, problèmes, LOCATE, sortie comparateur. |
| **Farm Connector** | Outil de configuration (non consommé). |
| **Irrigation Pump** | Cœur hydraulique : exige une vraie source d'eau adjacente (ne crée jamais d'eau), ou se pose **sous l'eau** dans une source (bloc immergé), appareil HomeCore. |
| **Copper Irrigation Pipe** | Tuyau connecté automatiquement sur ses 6 faces ; s'oxyde comme le cuivre vanilla (4 stades + 4 variantes cirées). |
| **Copper Sprinkler** | Irrigue la zone autour de lui tant que son réseau est ACTIVE. Se pose sur un tuyau, ou **dessous** (clic sur la face inférieure) pour être suspendu, tête vers le bas. |

Les appareils connectés (Controller relié à au moins un composant ou à un réseau HomeLink, Monitor et Pump reliés à un Controller ou à un réseau HomeLink) font clignoter leurs voyants lumineux, visibles aussi la nuit.

### Farm Connector

- Clic droit sur un **Farm Controller** : le sélectionne (`Farm Controller selected`).
- Clic droit sur un **Crop Monitor** ou une **Irrigation Pump** : le relie au contrôleur sélectionné.
- **Accroupi + clic droit** sur deux blocs : position A puis position B d'une zone ; **accroupi + clic droit** sur un Crop Monitor : applique la zone.
- **Accroupi + utiliser dans le vide** : efface la sélection.

En tenant le connecteur, le contrôleur sélectionné est entouré en vert et la zone en cours de sélection s'affiche en doré, de A jusqu'au bloc visé puis jusqu'à B.

Toute la logique passe par `FarmLinkService` (serveur) : un futur connecteur universel HomeLink pourra le remplacer sans changer les blocs.

Un Crop Monitor posé surveille par défaut 9 × 9 blocs autour de lui (une couche en dessous à une au-dessus). Son écran propose aussi *Zone 9×9*, *Effacer*, *Rescanner*, *Diagnostic / Localiser*, *Voir l'irrigation*, *Voir zone* (contour cyan de la zone pendant 10 s) et le mode comparateur. Chaque objet décrit son rôle dans son infobulle.

## Règles de jeu

### Surveillance et diagnostic

- Cultures reconnues via l'API `CropAdapter` : blé, carottes, pommes de terre, betteraves, torchflower (et toute `CropBlock` moddée), tiges de melon/citrouille, pitcher plant, verrue du Nether, baies sucrées, cacao.
- Maturité normalisée 0 → 100 %, cultures prêtes et en croissance.
- Problèmes signalés **uniquement quand Minecraft fournit un signal fiable**, jamais sur une culture mûre :
  - `DRY_FARMLAND` : terre labourée d'humidité 0 (croissance vanilla ~3 × plus lente) ;
  - `LOW_LIGHT` : lumière brute < 9, seuil du `randomTick` vanilla (aucune croissance) ;
  - `NOT_IRRIGATED` : culture irrigable hors de tout arroseur **dans une zone déjà irriguée en partie** ;
  - `IRRIGATION_OFFLINE` : couverte uniquement par des arroseurs dont le réseau ne fonctionne pas.
- Une culture « bloquée » n'est pas détectable de façon fiable : elle n'est pas signalée.
- **LOCATE** : le serveur vérifie que l'écran du moniteur est ouvert et que la position fait partie de ses problèmes actuels, puis envoie au seul joueur concerné un marqueur temporaire (contour, faisceau, particules) qui ne modifie aucun bloc.

### Réseau d'irrigation

- **Une pompe = 5 arroseurs maximum.** Un réseau avec N pompes alimentées accepte 5 × N arroseurs.
- Au-delà, **tout le réseau passe `OVER_CAPACITY`** : aucun arroseur n'irrigue, la pompe et les arroseurs s'affichent en rouge. Règle déterministe : le joueur n'a jamais à deviner quels arroseurs fonctionnent.
- L'oxydation est purement esthétique : tous les stades transportent l'eau à l'identique.
- Le réseau est recalculé uniquement lors d'un changement structurel (tuyau, pompe ou arroseur posé ou retiré, chunk d'une pompe ou d'un arroseur déchargé), plus une reconstruction de sécurité toutes les 60 s. Jamais de parcours complet à chaque tick.

### Couverture d'un arroseur

Carré de `2 × sprinklerRange + 1` blocs centré sur l'arroseur (**5 × 5** par défaut). Verticalement :
- arroseur **posé** : de 2 blocs en dessous à 1 bloc au-dessus (cultures à son niveau, ou un niveau plus bas s'il est surélevé sur un tuyau) ;
- arroseur **suspendu** sous un tuyau : de 3 blocs en dessous jusqu'à son niveau, pour arroser depuis des tuyaux passant au-dessus de la tête.

Un arroseur actif hydrate aussi la terre labourée de sa zone, via le `FarmlandWaterManager` de NeoForge.

### Bonus de croissance : +20 %

- Vanilla donne à chaque bloc en moyenne `randomTickSpeed / 4096` tick aléatoire par tick de jeu. La chance de pousser par tick aléatoire est fixe.
- Toutes les 20 ticks, chaque culture irriguée et compatible reçoit en moyenne `0,20 × randomTickSpeed × 20 / 4096` tick aléatoire **supplémentaire** (0,00293 avec les valeurs par défaut). C'est exactement +20 % d'occasions de croissance, donc une croissance 1,20 × plus rapide en moyenne. Toutes les règles vanilla (lumière, terre, événements NeoForge) s'appliquent à ces ticks.
- Seules les cultures couvertes par un arroseur d'un réseau ACTIVE, et situées dans des chunks où les ticks aléatoires ont lieu, sont concernées.
- **Aucun cumul** : la couverture est une union de positions, et une culture sous 2 ou 3 arroseurs n'est tirée qu'une fois.
- Le gamerule `randomTickSpeed` est seulement lu, jamais modifié. Le bonus ne touche ni les drops, ni Fortune, ni les quantités.

### Redstone

- **Crop Monitor → comparateur** (mode configurable) : `MATURITY` par défaut (0 % → 0, 100 % → 15), `READY`, `IRRIGATION`, `PROBLEMS` (nombre plafonné à 15).
- **Irrigation Pump** : mode `IGNORED` (par défaut), `RUN_WHEN_POWERED` ou `STOP_WHEN_POWERED`.
- **Pompe → comparateur** : nombre d'arroseurs alimentés (1 à 14) si ACTIVE, 15 en panne (pas d'eau, surcharge, réseau trop grand), 0 sinon.

### Pause sous la pluie

Non implémentée, volontairement. La pluie hydrate la terre mais n'accélère pas la croissance : suspendre les arroseurs sous la pluie ne ferait que retirer le bonus, sans cohérence avec le jeu vanilla.

## Vue irrigation (SHOW IRRIGATION)

Touche **`I`** (configurable), ou bouton *Voir l'irrigation* dans les écrans du moniteur et de la pompe. L'affichage est temporaire et uniquement côté client, tracé au niveau des cultures (un bloc plus bas quand l'arroseur est surélevé sur un tuyau) avec une teinte légère :
- carré bleu : zone irriguée ;
- carré rouge : réseau en panne ;
- carré gris : arroseur non alimenté ;
- cadres orange : cultures non irriguées.

Les données (arroseurs et cultures non couvertes à moins de 64 blocs) sont demandées au serveur au plus une fois par seconde. Seuls des contours et un aplat par arroseur sont dessinés, jusqu'à 96 blocs de distance.

## Intégration HomeCore

HomeLink Farm consomme uniquement l'API publique `fr.lkdm.homecore.api` : `DashboardAPI`, `DashboardDevice`, `DeviceMetric`, `DeviceAction`, `DeviceEvent` et `HomeNetworkManager`. Il ne dépend ni de HomeLink Dashboard, ni de HomeLink Tasks, ni de HomeLink Storage, ni de Holographique Map : ces mods lisent les données génériques via HomeCore.

Le Farm Controller et l'Irrigation Pump s'enregistrent via le système provider + `discover` au chargement de leur bloc, et se désenregistrent au déchargement. En cas de collision d'UUID (bloc copié), la copie reçoit une nouvelle identité.

Un appareil se rattache à un **HomeNetwork** via le bouton *HomeLink : réseau* de son écran. Il faut la permission `MANAGE_NETWORK` sur le nouveau réseau comme sur l'ancien. Un appareil détruit est retiré de son réseau.

| Appareil | Métriques | Actions | Événements |
| --- | --- | --- | --- |
| `homelink_farm:farm_controller` | `crop_count`, `ready_percentage`, `maturity`, `irrigation_coverage`, `irrigated_crops`, `problem_count`, `crop_areas`, `pumps`, `sprinklers_connected`, `sprinkler_capacity`, `growth_bonus` | `rescan` (CONTROL) | `crop_ready`, `problem_detected`, `irrigation_failure`, `irrigation_restored` |
| `homelink_farm:irrigation_pump` | `pump_status`, `enabled`, `water_available`, `sprinklers_connected`, `sprinkler_capacity`, `irrigated_crops`, `over_capacity` | `enabled` (toggle, CONTROL) | `pump_over_capacity`, `irrigation_failure`, `irrigation_restored` |

Tous les identifiants sont dans l'espace de noms `homelink_farm`. Les événements sont émis **sur transition uniquement** :
- `crop_ready` : au franchissement de 90 % de cultures prêtes, réarmé sous 70 % ;
- `problem_detected` : quand le nombre de problèmes passe de 0 à plus de 0 ;
- `pump_over_capacity` : par exemple au passage de 5 à 6 arroseurs.

La première observation après un chargement est silencieuse.

Les actions passent exclusivement par `DashboardAPI.executeAction`, qui vérifie l'appartenance au réseau, les permissions, l'état ONLINE, le paramètre et le rate limit.

## Langues

Tout le mod est disponible en **français** et en **anglais** : blocs, objets, infobulles, écrans, messages, touches, écran de configuration, description dans la liste des mods, noms des métriques HomeCore, nombres au format de la langue (64,3 %). Un test vérifie que chaque texte utilisé par le code existe dans les deux langues.

## Configuration serveur (`homelink_farm-server.toml`)

Modifiable en jeu : *Mods → HomeLink Farm → Configurer* (en solo), ou dans le fichier.

| Clé | Défaut | Rôle |
| --- | --- | --- |
| `controller.maxLinkDistance` | 64 | Distance maximale contrôleur ↔ composant |
| `controller.maxComponentsPerController` | 32 | Composants par contrôleur |
| `cropMonitor.maxCropMonitorVolume` | 32768 | Volume maximal d'une zone (32 × 32 × 32) |
| `cropMonitor.maxZoneDistance` | 48 | Distance maximale moniteur ↔ zone |
| `cropMonitor.cropScanInterval` | 100 | Ticks entre deux passes d'analyse |
| `cropMonitor.cropScanBudgetPerTick` | 512 | Positions analysées par moniteur et par tick |
| `cropMonitor.globalScanBudgetPerTick` | 8192 | Positions analysées par tick, tous moniteurs confondus |
| `cropMonitor.locateDuration` | 400 | Durée d'un marqueur LOCATE (ticks) |
| `irrigation.maxSprinklersPerPump` | **5** | Règle officielle : 5 arroseurs par pompe |
| `irrigation.maxNetworkNodes` | 1024 | Taille maximale d'un réseau |
| `irrigation.sprinklerRange` | 2 | Portée horizontale (2 = 5 × 5) |
| `irrigation.irrigationGrowthBonus` | **0.20** | Bonus de croissance (+20 %) |

## Sécurité et performances

- **Le serveur calcule toute la vérité** : maturité, couverture, bonus, validité des pompes et des réseaux.
- **Chaque requête client est revalidée** : écran réellement ouvert, portée de 8 blocs, propriétaire, opérateur ou permission HomeCore `CONFIGURE`, limites de zone et de distance.
- **Analyse incrémentale** : budget par moniteur et budget global par tick.
- **Aucun chargement forcé de chunk** : un chunk déchargé est marqué « partiel » ; le contrôleur réutilise alors les dernières valeurs connues, signalées comme obsolètes.
- **Caches non sauvegardés** : résultats d'analyse, couverture, réseaux. Seules la configuration et l'identité sont persistées.
- **Synchronisation** : les clients ne reçoivent les données qu'en cas de changement. La liste des problèmes n'est envoyée qu'aux joueurs qui regardent le moniteur.
- **Mesures en GameTest** : zone maximale de 32 768 blocs analysée en 64 ticks (moins de 3 ms au pire tick, démarrage de la JVM compris) ; couverture de 40 arroseurs reconstruite en environ 1 ms ; passe de croissance à environ 0,004 ms toutes les 20 ticks.

## API pour les autres mods

```java
CropAdapters.register(new MyCropAdapter()); // pendant le setup ; les adaptateurs enregistrés en dernier ont la priorité
```

`CropAdapter` décrit l'âge, l'âge maximal, la maturité, la terre labourée, la compatibilité avec l'irrigation, le seuil de lumière et l'application d'un tick de croissance.

## Vérification

```powershell
./gradlew.bat build                                        # compilation + tests JUnit
./gradlew.bat runGameTestServer                            # GameTests serveur (échec si un test échoue)
./gradlew.bat runPersistence -PpersistencePass=write       # redémarrage réel : écrit un monde...
./gradlew.bat runPersistence -PpersistencePass=read        # ...puis le relit dans un nouveau processus
./gradlew.bat runClientSmoke                               # vrai client : scénario complet + captures d'écran
./gradlew.bat runClientSmoke -PsmokeLanguage=fr_fr         # le même scénario, jeu en français
./gradlew.bat runClientSmoke -PsmokeScenario=player        # vrai client joué via les entrées joueur (clics, touche I, sauvegarde/rechargement, mesure du +20 %)
```

Les GameTests, le smoke client (captures dans `build/client-smoke/screenshots`) et les fixtures sont dans le source set `gametest`, **exclu du JAR**.

## Limites connues

- Les textures sont des placeholders générés, à remplacer par des textures définitives.
- Casser un bloc fait perdre son nom et sa configuration (l'objet ne les conserve pas).
- La vue irrigation affiche au plus 64 problèmes par moniteur et 256 arroseurs.
- Pas d'Advanced Sprinkler ni d'Irrigation Tank en V1.
- Le transport HomeCore envoie des textes : ils sont traduits en solo et restent en anglais sur un serveur dédié. Les identifiants de métriques permettent de les retraduire côté Dashboard.
