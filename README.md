# HomeLink Farm 1.1.0

Module agricole de l'écosystème HomeLink : surveiller, diagnostiquer, irriguer, optimiser et connecter une exploitation Minecraft. Aucune ressource n'est jamais créée ; seul le **FarmBot**, un robot que le joueur fabrique et installe, récolte et replante, en se déplaçant réellement jusqu'aux cultures.

**Minecraft 1.21.1 · NeoForge 21.1.250+ · Java 21 · HomeCore 1.7.0+ (obligatoire).**

```text
Farm → Crop Monitor → Farm Controller → HomeCore        Water → Irrigation Pump → Copper Pipes → max 5 Sprinklers → +20 % de croissance
Crop Monitor(s) → FarmBot Station → FarmBot : roule → récolte → replante → revient → décharge → se recharge   (Farm Controller facultatif)
```

## Installation

1. Installer **HomeCore 1.9.0** ([LKDM7/HomeCore](https://github.com/LKDM7/HomeCore)) et **HomeLink Energy 0.2.0** côté client et serveur. Le contrôleur, les pompes et les stations FarmBot implémentent `NetworkMember` : le Dashboard peut les lister dans sa zone radio et les ajouter à un réseau, avec le même rattachement que leur bouton HomeLink.
2. Installer `homelink_farm-1.1.0.jar` dans `mods` côté client et serveur.

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
| **Copper Irrigation Pipe** | Tuyau connecté automatiquement sur ses 6 faces. S'oxyde tout seul, au même rythme quel que soit le cuivre voisin : neuf → exposé → altéré → oxydé en **environ 100 jours de jeu** (réglable, chunks chargés). 4 stades + 4 variantes cirées ; la hache gratte, le rayon de miel cire. Purement esthétique : tous les stades transportent l'eau. |
| **Copper Sprinkler** | Irrigue la zone autour de lui tant que son réseau est ACTIVE. Se pose sur un tuyau, ou **dessous** (clic sur la face inférieure) pour être suspendu, tête vers le bas. Peut être immergé (waterlogged). Éclaire comme une torche (niveau 14). |
| **FarmBot Station** | Dock, chargeur, point de départ et de retour d'un FarmBot, sortie de 9 emplacements, commandes START / PAUSE / RETURN HOME, appareil HomeCore. Le robot se gare sur le bloc **devant** la station (face FACING). Fonctionne seule avec le Crop Monitor le plus proche, ou sur toute une ferme via un Farm Controller. |
| **FarmBot** | Robot agricole autonome (entité, environ 0,8 bloc de large) : 9 emplacements, batterie interne ; récolte et replante les cultures mûres signalées par le Crop Monitor de sa station. |

Les appareils connectés (Controller relié à au moins un composant ou à un réseau HomeLink, Monitor et Pump reliés à un Controller ou à un réseau HomeLink) font clignoter leurs voyants lumineux, visibles aussi la nuit.

Chaque écran de machine possède un bouton **?** dans son en-tête. Il ouvre un guide adapté au contrôleur, au moniteur ou à la pompe : installation, commandes, liaisons et dépannage. Faites défiler avec la molette, les flèches ou Page précédente/suivante ; **Retour à la machine** ou **Échap** revient aux commandes en conservant le nom en cours de saisie. Les guides sont disponibles en français et en anglais.

### Farm Connector

- Clic droit sur un **Farm Controller** : le sélectionne (`Farm Controller selected`).
- Clic droit sur un **Crop Monitor** ou une **Irrigation Pump** : le relie au contrôleur sélectionné.
- **Accroupi + clic droit** sur deux blocs : position A puis position B d'une zone ; **accroupi + clic droit** sur un Crop Monitor : applique la zone.
- **Accroupi + utiliser dans le vide** : efface la sélection.

En tenant le connecteur, le contrôleur sélectionné est entouré en vert et la zone en cours de sélection s'affiche en doré, de A jusqu'au bloc visé puis jusqu'à B.

Toute la logique passe par `FarmLinkService` (serveur) : un futur connecteur universel HomeLink pourra le remplacer sans changer les blocs.

Un Crop Monitor posé surveille par défaut **tout son chunk** : 16 × 16 blocs, du bas du monde jusqu'à la limite de construction (16 × 384 × 16 dans l'Overworld). Cette zone, construite par le serveur autour du moniteur, échappe aux limites de volume et de distance des zones tracées au Farm Connector ; son analyse reste étalée par le budget par tick (environ 10 s par passe). Son écran propose aussi *Zone 16×16*, *Effacer*, *Rescanner*, *Diagnostic / Localiser*, *Voir l'irrigation*, *Voir zone* (contour cyan de la zone pendant 10 s) et le mode comparateur. Chaque objet décrit son rôle dans son infobulle.

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
- **LOCATE** : le serveur vérifie que l'écran du moniteur est ouvert et que la position fait partie de ses problèmes actuels, puis envoie au seul joueur concerné un marqueur temporaire : coins lumineux, petit losange flottant et particules. Il s'estompe avant de disparaître et ne modifie aucun bloc.

### Réseau d'irrigation

- **Une pompe = 5 arroseurs maximum.** Un réseau avec N pompes alimentées accepte 5 × N arroseurs.
- Au-delà, **tout le réseau passe `OVER_CAPACITY`** : aucun arroseur n'irrigue, la pompe et les arroseurs s'affichent en rouge. Règle déterministe : le joueur n'a jamais à deviner quels arroseurs fonctionnent.
- L'oxydation est purement esthétique : tous les stades transportent l'eau à l'identique.
- Le réseau est recalculé uniquement lors d'un changement structurel (tuyau, pompe ou arroseur posé ou retiré, chargement ou déchargement d'un chunk du réseau, même s'il ne contient que des tuyaux), plus une reconstruction de sécurité toutes les 60 s. Le chargement d'un chunk voisin d'un réseau incomplet invalide aussi son cache. Jamais de parcours complet à chaque tick.

### Couverture d'un arroseur

Carré de `2 × sprinklerRange + 1` blocs centré sur l'arroseur (**5 × 5** par défaut). Verticalement :
- arroseur **posé** : de 12 blocs en dessous à 1 bloc au-dessus ;
- arroseur **suspendu** sous un tuyau : de 12 blocs en dessous jusqu'à son niveau. La hauteur se mesure entre le bloc de l'arroseur et celui des cultures.

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

## FarmBot

### Installation

1. Poser une **FarmBot Station** : sa face avant regarde le joueur, le robot se garera juste devant.
2. Choisir les cultures. **Sans Farm Controller**, la station travaille avec le Crop Monitor le plus proche qui appartient au même joueur, dans la portée de liaison (64 blocs par défaut) ; le bouton *Moniteur* passe aux autres moniteurs proches. **Avec un Farm Controller** (facultatif, relié au Farm Connector comme un moniteur ou une pompe), le robot travaille sur **toute la ferme**, c'est-à-dire tous les moniteurs du contrôleur ; *Moniteur* permet d'en épingler un seul, puis de revenir à toute la ferme.
3. Clic droit sur la station avec un **FarmBot** : le serveur vérifie les droits, que la station est libre et que l'emplacement devant elle est dégagé, puis le robot apparaît à quai. Une station a un seul robot ; un robot n'appartient qu'à une station.

### Cycle de travail

Le robot ne scanne jamais la ferme : pendant son analyse, le Crop Monitor retient les positions des cultures mûres récoltables (512 au plus). Le robot choisit la plus proche qui est toujours mûre sur le serveur, dans la zone du moniteur et dans un chunk chargé. Il s'y rend par pathfinding (il contourne murs, clôtures, trous et eau, et roule entre les rangs sans piétiner la terre labourée ni abîmer les jeunes cultures), la récolte, puis la replante avec une partie de la récolte : une graine pour le blé, une carotte pour la carotte… Sans rien à replanter, l'emplacement reste vide : rien n'est créé.

Il revient à sa station quand sa batterie atteint **20 %**, quand ses 9 emplacements sont occupés (STORAGE FULL), quand il n'y a plus rien à récolter, ou sur *Retour station*. À quai, il décharge progressivement dans la sortie de la station puis se recharge (environ 25 s de 0 à 100 %) ; il repart à partir de 80 % si des cultures sont mûres. Si la sortie est pleine, il garde son chargement et attend (OUTPUT BLOCKED).

États : DOCKED, IDLE, SEARCHING, MOVING, HARVESTING, RETURNING, UNLOADING, CHARGING, PAUSED, STORAGE FULL, OUTPUT BLOCKED, LOW BATTERY, OUT OF POWER, STUCK, ERROR. Le voyant de l'antenne les résume : cyan en route, orange en récolte, vert pulsé en charge, ambre clignotant batterie faible, rouge clignotant bloqué.

### Stratégies de récolte (CropAdapter)

| Culture | Stratégie |
| --- | --- |
| Blé, carottes, pommes de terre, betteraves, cultures modées dérivées de `CropBlock` | cassée puis replantée à l'âge 0 |
| Verrues du Nether, cacao | cassés puis replantés (le cacao garde sa face) |
| Baies sucrées | cueillies, le buisson reste (âge 1) |
| Tiges de melon et de citrouille, plante carnivore | non récoltées |

### Robustesse

- **Aucune perte ni duplication** : ce qui ne rentre pas dans le robot tombe au sol sur la culture. Ramasser le robot (accroupi + clic droit) rend son chargement ; le frapper, un `/kill` ou le vide le font tomber avec son chargement. Les autres dégâts l'épargnent.
- **Batterie vide** : il s'arrête sur place (OUT OF POWER), sans téléportation. On le ramasse, ou on le pousse sur son quai pour qu'il se recharge.
- **Cible inaccessible** : après 3 tentatives, la culture est ignorée pendant une minute (TARGET UNREACHABLE). Station inaccessible ou quai obstrué : STUCK, avec un nouvel essai toutes les 5 s.
- **Chunks** : le robot ne charge aucun chunk. Une cible dans un chunk déchargé est ignorée ; si sa station est déchargée, il attend sur place.
- **Station cassée** : sa sortie tombe au sol ; le robot reste là, en ERROR, jusqu'à ce qu'on le ramasse.
- **Plusieurs robots** sur un même moniteur se réservent leurs cibles et ne visent jamais la même plante.

## Vue irrigation (SHOW IRRIGATION)

Touche **`I`** (configurable), ou bouton *Voir l'irrigation* dans les écrans du moniteur et de la pompe. Des repères fins suivent le relief juste au-dessus des cultures, avec une pulsation lente et un petit halo autour des arroseurs :

- turquoise : zone irriguée ;
- corail : réseau en panne ;
- gris : arroseur non alimenté ;
- petites croix dorées : cultures non irriguées, limitées aux douze signalements les plus proches pour préserver la lisibilité.

Les couvertures superposées sont réunies ; une couverture active prend la priorité. Les terrasses et les hauteurs réelles du terrain sont prises en compte jusqu'à la portée verticale de l'arroseur. Les relevés du terrain sont mis en cache, calculés par lots de huit arroseurs par tick et limités aux chunks déjà chargés. Les données sont rafraîchies toutes les deux secondes ; les repères s'estompent à distance.

Les zones du moniteur et du connecteur utilisent des coins lumineux et des pointillés animés : turquoise pour la zone surveillée, doré pour la sélection. « Voir zone » dure dix secondes, avec une apparition et une disparition progressives.

## Intégration HomeCore

HomeLink Farm consomme uniquement l'API publique `fr.lkdm.homecore.api` : `DashboardAPI`, `DashboardDevice`, `DeviceMetric`, `DeviceAction`, `DeviceEvent` et `HomeNetworkManager`. Il ne dépend ni de HomeLink Dashboard, ni de HomeLink Tasks, ni de HomeLink Storage, ni de Holographique Map : ces mods lisent les données génériques via HomeCore.

Le Farm Controller, l'Irrigation Pump et la FarmBot Station s'enregistrent via le système provider + `discover` au chargement de leur bloc, et se désenregistrent au déchargement. En cas de collision d'UUID (bloc copié), la copie reçoit une nouvelle identité.

Un appareil se rattache à un **HomeNetwork** via le bouton *HomeLink : réseau* de son écran. Il faut la permission `MANAGE_NETWORK` sur le nouveau réseau comme sur l'ancien. Un appareil détruit est retiré de son réseau.

| Appareil | Métriques | Actions | Événements |
| --- | --- | --- | --- |
| `homelink_farm:farm_controller` | `crop_count`, `ready_percentage`, `maturity`, `irrigation_coverage`, `irrigated_crops`, `problem_count`, `crop_areas`, `pumps`, `sprinklers_connected`, `sprinkler_capacity`, `growth_bonus` | `rescan` (CONTROL) | `crop_ready`, `problem_detected`, `irrigation_failure`, `irrigation_restored` |
| `homelink_farm:farmbot_station` | `farmbot_installed`, `farmbot_status`, `farmbot_battery`, `farmbot_storage`, `farmbot_harvested`, `farmbot_current_target`, `station_output_usage` | `start`, `pause`, `return_home` (boutons, CONTROL) | `farmbot_low_battery`, `farmbot_storage_full`, `farmbot_stuck`, `farmbot_output_blocked`, `farmbot_returned`, `farmbot_harvest_complete` |
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
| `cropMonitor.maxCropMonitorVolume` | 32768 | Volume maximal d'une zone tracée au Farm Connector (32 × 32 × 32) ; la zone du chunk entier n'est pas limitée |
| `cropMonitor.maxZoneDistance` | 48 | Distance maximale moniteur ↔ zone |
| `cropMonitor.cropScanInterval` | 100 | Ticks entre deux passes d'analyse |
| `cropMonitor.cropScanBudgetPerTick` | 512 | Positions analysées par moniteur et par tick |
| `cropMonitor.globalScanBudgetPerTick` | 8192 | Positions analysées par tick, tous moniteurs confondus |
| `cropMonitor.locateDuration` | 400 | Durée d'un marqueur LOCATE (ticks) |
| `irrigation.maxSprinklersPerPump` | **5** | Règle officielle : 5 arroseurs par pompe |
| `irrigation.maxNetworkNodes` | 1024 | Taille maximale d'un réseau |
| `irrigation.sprinklerRange` | 2 | Portée horizontale (2 = 5 × 5) |
| `irrigation.irrigationGrowthBonus` | **0.20** | Bonus de croissance (+20 %) |
| `irrigation.pipeOxidationDays` | 100 | Jours de jeu pour qu'un tuyau non ciré soit totalement oxydé |
| `farmbot.farmbotBatteryCapacity` | 1000 | Énergie d'une batterie pleine (mécanique interne, pas des FE) |
| `farmbot.farmbotLowBatteryThreshold` | **20** | Pourcentage de retour à la station |
| `farmbot.farmbotMovementConsumption` | 1.0 | Énergie par bloc parcouru |
| `farmbot.farmbotHarvestConsumption` | 3.0 | Énergie par culture récoltée |
| `farmbot.farmbotIdleConsumption` | 0.0 | Énergie par minute en attente hors de la station |
| `farmbot.farmbotRechargeTime` | 25 | Secondes de 0 à 100 % à quai |
| `farmbot.farmbotTargetRetryLimit` | 3 | Échecs avant d'ignorer une cible |
| `farmbot.farmbotSearchCooldown` | 40 | Ticks entre deux recherches sans résultat |

## Sécurité et performances

- **Le serveur calcule toute la vérité** : maturité, couverture, bonus, validité des pompes et des réseaux.
- **Chaque requête client est revalidée** : écran réellement ouvert, portée de 8 blocs, propriétaire, opérateur ou permission HomeCore `CONFIGURE`, limites de zone et de distance.
- **Analyse incrémentale** : budget par moniteur et budget global par tick. Une petite zone ne consomme que son nombre de positions restantes ; un moniteur sans budget ne reconstruit pas la couverture d'irrigation.
- **Aucun chargement forcé de chunk** : un chunk déchargé est marqué « partiel » ; le contrôleur réutilise alors les dernières valeurs connues, signalées comme obsolètes.
- **Caches non sauvegardés** : résultats d'analyse, couverture, réseaux. Seules la configuration et l'identité sont persistées.
- **Synchronisation** : les clients ne reçoivent les données qu'en cas de changement. La liste des problèmes n'est envoyée qu'aux joueurs qui regardent le moniteur.
- **FarmBot** : recherche de cible dans la liste du moniteur (512 positions) en 0,1 ms, au plus toutes les 2 s par robot ; chemin recalculé au plus une fois par seconde.
- **Mesures en GameTest** : zone maximale de 32 768 blocs analysée en 64 ticks (moins de 10 ms au pire tick, mesuré après le préchauffage de la JVM) ; couverture de 40 arroseurs reconstruite en environ 1 ms ; passe de croissance à environ 0,004 ms toutes les 20 ticks.

## API pour les autres mods

```java
CropAdapters.register(new MyCropAdapter()); // pendant le setup ; les adaptateurs enregistrés en dernier ont la priorité
```

`CropAdapter` décrit l'âge, l'âge maximal, la maturité, la terre labourée, la compatibilité avec l'irrigation, le seuil de lumière et l'application d'un tick de croissance, ainsi que la récolte par le FarmBot : `harvestMode` (`REPLANT`, `KEEP_PLANT` ou `NONE`), `harvestDrops`, `replantItem`, `replantState` et `harvestedState`.

## Vérification

```powershell
./gradlew.bat build                                        # compilation + tests JUnit
./gradlew.bat runGameTestServer                            # GameTests serveur (échec si un test échoue)
./gradlew.bat runPersistence -PpersistencePass=write       # redémarrage réel : écrit un monde...
./gradlew.bat runPersistence -PpersistencePass=read        # ...puis le relit dans un nouveau processus
./gradlew.bat runClientSmoke                               # vrai client : scénario complet + captures d'écran
./gradlew.bat runClientSmoke -PsmokeLanguage=fr_fr         # le même scénario, jeu en français
./gradlew.bat runClientSmoke -PsmokeScenario=player        # vrai client joué via les entrées joueur (clics, touche I, sauvegarde/rechargement, mesure du +20 %)
./gradlew.bat runClientSmoke -PsmokeScenario=help -PsmokeLanguage=fr_fr # guides des trois machines, défilement, redimensionnement et conservation du nom
./gradlew.bat runClientSmoke -PsmokeScenario=overlays -PsmokeLanguage=fr_fr # terrain en terrasses, irrigation active/arrêtée/en panne, zones et diagnostic
./gradlew.bat runClientSmoke -PsmokeScenario=farmbot       # FarmBot à quai, écrans de la station, récolte réelle d'un champ, voyant de nuit
```

Les GameTests, le smoke client (captures dans `build/client-smoke/screenshots`) et les fixtures sont dans le source set `gametest`, **exclu du JAR**.

## Limites connues

- Les boîtiers utilisent un atlas commun olive, graphite, acier et cuivre ; les sources et le script des modèles sont documentés dans [art/README.md](art/README.md).
- Casser un bloc fait perdre son nom et sa configuration (l'objet ne les conserve pas).
- La vue irrigation affiche au plus 64 problèmes par moniteur et 256 arroseurs.
- Pas d'Advanced Sprinkler ni d'Irrigation Tank en V1.
- Le transport HomeCore envoie des textes : ils sont traduits en solo et restent en anglais sur un serveur dédié. Les identifiants de métriques permettent de les retraduire côté Dashboard.
