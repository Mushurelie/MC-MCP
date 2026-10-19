# Changelog

Toutes les modifications notables de ce projet sont documentees dans ce
fichier.

## [Non publie]

### Ajoute

- Module `forge-1.20.1` (Forge 47.x, Java 17, mappings officiels) : les 49 outils
  portes depuis 1.16.5/1.12.2 et branches dans `DefaultToolRegistryFactory`.
- `save_structure` / `load_structure` implementes en 1.20.1 (format structure vanilla
  `.nbt`, entites incluses, rotation et miroir) ; `load_structure` accepte aussi les
  structures fournies par un datapack ou un mod (`namespace:nom`).
- `set_block`, `fill_area` et `generate_shape` acceptent en 1.20.1 un etat de bloc au
  format commande (`minecraft:oak_stairs[facing=north]`, NBT `{...}` optionnel).
- Parametre `dimension` : accepte les dimensions de mods (`ad_astra:moon`...) en 1.20.1.
- `screenshot` renvoie en 1.20.1 un vrai bloc image MCP ; `ContentBlock.rawContent`
  (common) permet a un outil de fournir directement ses blocs de contenu.

### Corrige

- 1.20.1 : le serveur HTTP n'est plus arrete en quittant une partie (il ne pouvait
  plus redemarrer sans relancer le jeu).

## [0.1.0] - 2026-08-03

### Ajoute

- Serveur MCP (Model Context Protocol) embarque, base sur
  `com.sun.net.httpserver.HttpServer` (zero dependance externe), demarre
  cote client dans `FMLInitializationEvent` (solo uniquement).
- Implementation JSON-RPC 2.0 complete : `initialize`, `notifications/initialized`,
  `tools/list`, `tools/call`, avec gestion des notifications (pas de reponse)
  et des erreurs JSON-RPC standard.
- `MainThreadDispatcher` : toute action touchant au monde/joueur/entites/
  inventaire est schedulee sur le thread client ou le thread serveur integre.
- Configuration Forge (`@Config`) : port, bind address, `readOnlyMode`,
  whitelist/blacklist d'outils, confirmation en jeu avec timeout, niveau de
  log, jeton bearer optionnel, volume maximum pour les outils de masse.
- Securite : bind `127.0.0.1` par defaut, verification de l'entete `Origin`
  (CSRF), jeton bearer optionnel, mono-instance (refuse de demarrer si le
  port est occupe).
- Journalisation de chaque appel d'outil (nom, arguments tronques, duree,
  succes/erreur) via le logger Forge.
- Popup de confirmation en jeu (accepter/refuser/timeout) pour les outils en
  ecriture, activable via `requireConfirmation`.
- 49 outils repartis en 11 categories :
  - **Monde / blocs** : `get_block`, `set_block`, `fill_area`, `clone_area`,
    `find_nearest_block`, `scan_area`, `get_biome`, `get_light_level`,
    `raycast`.
  - **Joueur** : `get_player_state`, `teleport`, `set_gamemode`, `set_spawn`,
    `get_inventory`, `give_item`, `clear_slot`, `equip_slot`.
  - **Agent** : `walk_to` (TODO v2 : pathfinding avance), `look_at`, `jump`,
    `start_sneaking`, `mine_block`, `place_block_at`, `use_item`,
    `attack_entity`.
  - **Entites** : `list_entities`, `get_entity`, `spawn_entity`,
    `kill_entity`, `follow_entity` (TODO v2 : pathfinding avance).
  - **Chat & commandes** : `send_chat`, `read_chat_history`,
    `execute_command`, `send_actionbar`, `send_title`.
  - **Monde global** : `get_world_info`, `set_time`, `set_weather`.
  - **Craft & conteneurs** : `list_recipes`, `craft_item`, `open_container`,
    `read_container`, `transfer_items`.
  - **Construction** : `generate_shape` (cube/sphere/cylindre/pyramide),
    `save_structure` (TODO v2, non implemente), `load_structure`
    (TODO v2, non implemente).
  - **Observation** : `screenshot`, `get_camera_view`, `get_stats`,
    `get_achievements` (interroge le systeme d'Advancements).
  - **Redstone** : `interact_block`, `get_redstone_power`.
  - **Meta** : `ping`, `list_tools_meta`.
- Documentation : `README.md` (build, install, config, Claude Desktop, curl),
  `docs/TOOLS.md` (tableau exhaustif des outils).
- Traductions `en_us` / `fr_fr`.

### Connu / limitations

- `walk_to` et `follow_entity` sont des implementations simplifiees (ligne
  droite tick par tick), pas un vrai `PathNavigate`.
- `save_structure` / `load_structure` renvoient une erreur explicite
  "non implemente" (TODO v2).
- Mono-joueur uniquement (aucun support serveur dedie / multijoueur).
