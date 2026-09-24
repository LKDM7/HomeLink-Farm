# Direction artistique des blocs

Boîtiers agricoles vert olive, châssis graphite, fixations en acier galvanisé et conduites en cuivre. Les écrans et les voyants conservent leurs textures et animations dédiées pour distinguer les états liés, actifs, arrêtés et en panne.

## Sources

- `farm_materials_source.png` : atlas produit avec l'outil intégré imagegen.
- `../src/main/resources/assets/homelink_farm/textures/block/farm_materials.png` : version de jeu, 64 × 64 pixels, échantillonnée au centre des pixels sans interpolation. Quatre matériaux de 32 × 32 pixels, sans mipmaps personnalisés.
- `../tools/refine_models.py` : génération des neuf modèles JSON partagés. Exécuter `uv run --python 3.12 python tools/refine_models.py` depuis le dépôt, ou utiliser un Python 3 déjà installé.

Les variantes liées et les trois états hydrauliques héritent des modèles partagés. Les tuyaux gardent leurs textures de cuivre aux quatre stades d'oxydation, y compris les versions cirées. Les formes de sélection des appareils prennent en compte les nouveaux reliefs principaux.

## Prompt utilisé (outil intégré, sans API externe)

Create a production usable square Minecraft pixel-art MATERIAL TEXTURE ATLAS, not a picture of objects. The entire image is exactly a seamless 2 by 2 grid of four equal square material swatches touching with NO gutters, NO borders between quadrants, NO labels, NO text, NO perspective, NO shadows cast across quadrants. Upper left quadrant: muted agricultural olive green painted metal, mostly flat with subtle 2-tone pixel mottling and very sparse tiny worn paint chips. Upper right: graphite dark charcoal steel, subtle large pixel patches, quiet uniform material. Lower left: warm orange copper metal, subtle square pixel mottling, no rivets. Lower right: desaturated pale grey galvanized steel with restrained square pixel variation. Strict authentic Minecraft low resolution pixel art: the whole atlas visually a 64 by 64 logical pixel grid enlarged with crisp nearest-neighbor edges, each quadrant 32 by 32 logical pixels. No tiny detail, no gradients, no bevels, no bolts, no decorative frames, no symbols, no lighting effects. Flat evenly lit color texture suitable for mapping over block geometry. Restrained readable palette. Save the generated image as a local file for integration into the game project.

## Vérification visuelle

`./gradlew.bat runClientSmoke -PsmokeLanguage=fr_fr` contrôle les modèles de tous les états et objets, puis capture les blocs de jour et de nuit et les quatre appareils en gros plan dans `build/client-smoke/screenshots/smoke_model_*.png`.

`./gradlew.bat runClientSmoke -PsmokeScenario=textures -PsmokeLanguage=fr_fr` exécute le contrôle détaillé des textures : 25 captures sans interface, champ de vision de 30°, faces avant/arrière, huit variantes de tuyaux, arroseur suspendu vu dessous, pompe arrêtée/en panne/active et voyants nocturnes. Les captures sont dans `build/client-smoke/screenshots/smoke_texture_*.png` ; une sélection vérifiée est conservée dans `art/previews/textures/`.

### Correctifs de placement des textures

Les boîtiers et leurs garnitures ne partagent plus de faces opaques superposées (z-fighting). Les UV de l'atlas sont projetés à une densité constante d'un pixel de texture par unité du modèle, au lieu d'étirer un carré de matériau sur chaque face. Les panneaux et masques lumineux gardent la projection Minecraft d'origine.

`ModelAssetsTest` vérifie les faces coplanaires, la densité et les limites des UV de l'atlas, la résolution des références de textures et les dimensions des PNG et animations. Aucun nouveau bitmap n'a été généré pour cette correction : elle porte sur la géométrie et le placement des textures existantes.
