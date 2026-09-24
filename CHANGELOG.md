# Modifications non publiées

- Portée verticale des arroseurs posés et suspendus augmentée à 12 blocs vers le bas ; aide et tests des limites mis à jour.

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
