# Modifications non publiées

## Tuyaux et objet FarmBot

- Les arroseurs éclairent comme une torche (niveau de lumière 14), quel que soit leur état : les cultures voisines ont assez de lumière pour pousser la nuit.
- La recette de l'arroseur demande une poudre de glowstone (sous la grille en cuivre).
- Les tuyaux non cirés s'oxydent désormais tout seuls à rythme régulier : environ **100 jours de jeu** de neuf à totalement oxydé (`irrigation.pipeOxidationDays`). L'oxydation vanilla s'arrêtait presque dans une ligne de tuyaux, car elle ralentit fortement près d'autre cuivre.
- L'objet FarmBot affiche le vrai modèle 3D du robot, en main (première et troisième personne), dans l'inventaire, au sol et dans un cadre.

## Farm Controller facultatif pour le FarmBot

- Sans Farm Controller, une FarmBot Station travaille avec le Crop Monitor le plus proche appartenant au même joueur, dans la portée de liaison ; *Moniteur* passe aux autres moniteurs proches.
- Reliée à un Farm Controller, elle travaille sur **toute la ferme** (tous les Crop Monitors du contrôleur) ; *Moniteur* permet d'en épingler un seul. Le robot choisit la culture mûre la plus proche parmi tous ses moniteurs.
- Le moniteur d'un autre joueur n'est jamais utilisé.

## HomeCore 1.6.1 et recettes

- HomeLink Farm requiert désormais **HomeCore 1.6.1** (au lieu de 1.3.0).
- Les appareils électroniques utilisent les composants communs de HomeCore : microprocesseur pour le Farm Controller et le FarmBot, circuit imprimé pour le Crop Monitor, l'Irrigation Pump, le FarmBot et sa station. Tuyaux, arroseurs et Farm Connector ne changent pas.

## Crop Monitor

- Zone par défaut étendue à **tout le chunk**, du bas du monde à la limite de construction (16 × 384 × 16 dans l'Overworld), à la pose comme avec *Zone 16×16*. Construite par le serveur autour du moniteur, elle n'est pas soumise aux limites des zones tracées au Farm Connector ; son analyse reste répartie par le budget par tick. Les moniteurs déjà posés gardent leur zone jusqu'au prochain *Zone 16×16*.

## FarmBot

- Nouvelle **FarmBot Station** : dock, chargeur, sortie de 9 emplacements et commandes START / PAUSE / RETURN HOME. Elle se relie au Farm Controller avec le Farm Connector et travaille avec le Crop Monitor de la ferme le plus proche (bouton *Moniteur* pour en changer).
- Nouveau **FarmBot** : robot entité qui se déplace réellement jusqu'aux cultures mûres signalées par le Crop Monitor, les récolte, les replante avec une partie de la récolte, revient se vider et se recharger. Batterie interne (retour à 20 %), 9 emplacements, 15 états visibles sur son voyant et dans l'écran de la station.
- `CropAdapter` décrit désormais la récolte (`REPLANT`, `KEEP_PLANT`, `NONE`) : blé, carottes, pommes de terre, betteraves, verrues du Nether, cacao et baies sucrées sont pris en charge, ainsi que les cultures modées dérivées de `CropBlock`.
- Le Crop Monitor retient pendant son analyse les positions des cultures mûres récoltables (512 au plus, jamais sauvegardées).
- La station est un appareil HomeCore : 7 métriques, actions `start` / `pause` / `return_home` (permission CONTROL) et 6 événements publiés sur transition.
- Aucune perte ni duplication d'objets, aucun chunk chargé, pas de piétinement de la terre labourée. Section `farmbot` de la configuration serveur (8 réglages).
- 21 GameTests FarmBot et un scénario client dédié (`-PsmokeScenario=farmbot`).
- Tests de performance de l'analyse plus fiables : le pire tick est mesuré après le préchauffage de la JVM, et le test des chunks déchargés n'appelle plus l'analyse 20 fois dans le même tick.

- Portée verticale des arroseurs posés et suspendus augmentée à 12 blocs vers le bas ; aide et tests des limites mis à jour.
- Arroseurs immergeables (waterlogged) comme les tuyaux : l'eau qui coule ne les emporte plus.
- Nom personnalisé d'une machine affiché en doré dans l'en-tête de son écran.
- Bouton « Voir l'irrigation » doré tant que la vue est active (moniteur et pompe) ; la touche I fonctionne aussi dans ces écrans.

## Modèles et matériaux

- Tuyaux redessinés en conduites continues de quatre pixels, avec petits manchons aux jonctions ; formes de sélection assorties dans les six directions.

- Correction des faces superposées des boîtiers et garnitures, responsables du mélange et du clignotement des textures.
- Projection des matériaux à densité constante pour éviter leur étirement sur les petites faces ; coordonnées UV explicites.
- Contrôle visuel dédié de 25 vues rapprochées et quatre tests de non-régression sur les ressources graphiques.
- Palette commune olive, graphite, acier et cuivre, avec un atlas de jeu de 64 × 64 pixels.
- Contrôleur sur pieds avec casquette d'écran et aérations arrière ; moniteur avec socle renforcé et protection supérieure.
- Pompe avec ailettes de refroidissement, bandes en cuivre et fixations ; arroseur avec bagues et buses distinctes.
- Tuyaux amincis entre leurs raccords en relief ; conservation des variantes oxydées et cirées.
- Formes de sélection adaptées aux reliefs des appareils, et captures de contrôle rapprochées ajoutées au scénario client.

## Fiabilité et performances

- Invalidation des réseaux lors du chargement ou déchargement des chunks contenant uniquement des tuyaux, sans attendre la reconstruction de sécurité de 60 secondes.
- Budget d'analyse limité aux positions restant à examiner, pour laisser davantage de place aux autres moniteurs.
- Vérification du chargement du composant avant une tentative de liaison au contrôleur.

## Interface

- Aperçus des zones avec coins lumineux, pointillés animés et fondu ; diagnostics avec losange flottant à la place du grand faisceau.
- Vue irrigation adaptée au relief et aux terrasses, contours réunis entre arroseurs, halos animés et croix sur les cultures non couvertes. Relevés du terrain mis en cache et répartis entre les ticks.

- Bouton « ? » doré au survol et tant que le mode d'emploi est ouvert, sur les trois machines.

- Zone automatique du moniteur agrandie à 16 × 16 blocs, alignée sur son chunk, sur trois couches autour de sa hauteur ; bouton, infobulles et aide mis à jour. Les zones déjà enregistrées sont conservées jusqu'à l'utilisation du bouton « Zone 16×16 ».

- Bouton « ? » sur les trois machines, avec guides français et anglais, défilement à la molette et au clavier, retour aux commandes sans perdre le nom en cours de saisie.

- Effacement immédiat des anciens diagnostics envoyés aux joueurs lorsqu'une zone est supprimée ou remplacée.
- Conservation du nom en cours de saisie lors de la reconstruction des boutons ou du redimensionnement de l'écran.
- Actualisation des boutons de diagnostic uniquement quand les données changent, et mise à jour du mode comparateur en dehors du rendu.
- Vue d'irrigation sans doublons entre moniteurs ; distance vérifiée sur chaque culture, y compris quand son moniteur se trouve hors du rayon d'affichage.

## Vérification

- Tests de régression pour les petites zones, les notifications de chunks, la conservation des caches éloignés et le filtrage des cultures dans la vue d'irrigation.
- Compilation, tests unitaires, GameTests serveur et scénario client complet en français.
