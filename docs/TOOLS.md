# Tableau exhaustif des outils MCP

Tous les appels passent par `tools/call` avec l'enveloppe JSON-RPC 2.0
suivante (seul le contenu de `arguments` change d'un outil a l'autre) :

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "<nom_outil>",
    "arguments": { }
  }
}
```

Le contenu ci-dessous montre uniquement la valeur de `"arguments"` pour
chaque outil (exemple JSON-RPC = colonne "Exemple `arguments`"). `readOnly`
indique si l'outil est autorise quand `readOnlyMode=true` (config).

## Monde / blocs

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `get_block` | oui | `x, y, z` (int), `dimension?` (int) | `{pos, id, state, nbt?}` | `{"x":0,"y":64,"z":0}` |
| `set_block` | non | `x, y, z` (int), `block` (string), `state?` (int, meta 0-15), `dimension?` (int) | `{success, pos}` | `{"x":0,"y":64,"z":0,"block":"minecraft:stone"}` |
| `fill_area` | non | `x1,y1,z1,x2,y2,z2` (int), `block` (string), `mode?` (`replace`\|`keep`\|`hollow`\|`outline`), `dimension?` | `{success, blocksPlaced}` | `{"x1":0,"y1":64,"z1":0,"x2":10,"y2":70,"z2":10,"block":"minecraft:glass","mode":"hollow"}` |
| `clone_area` | non | `fromX/Y/Z, toX/Y/Z, destX/Y/Z` (int), `dimension?` | `{success, blocksCopied}` | `{"fromX":0,"fromY":64,"fromZ":0,"toX":5,"toY":68,"toZ":5,"destX":20,"destY":64,"destZ":20}` |
| `find_nearest_block` | oui | `type` (string), `center` (`{x,y,z}`), `radius` (int, max 48), `dimension?` | `{x,y,z,distance}` ou `{"found":null}` | `{"type":"minecraft:diamond_ore","center":{"x":0,"y":32,"z":0},"radius":32}` |
| `scan_area` | oui | `center` (`{x,y,z}`), `radius` (int, max 48), `groupBy?` (`block`\|`y`), `dimension?` | `{counts:{...}, groupBy}` | `{"center":{"x":0,"y":64,"z":0},"radius":16,"groupBy":"block"}` |
| `get_biome` | oui | `x, z` (int), `dimension?` | `{biome, rainfall, temperature}` | `{"x":0,"z":0}` |
| `get_light_level` | oui | `x, y, z` (int), `dimension?` | `{blockLight, skyLight, combined}` | `{"x":0,"y":64,"z":0}` |
| `raycast` | oui | `origin` (`{x,y,z}`), `direction` (`{x,y,z}`), `maxDist` (number) | `{hit, pos, block?, entity?}` | `{"origin":{"x":0,"y":70,"z":0},"direction":{"x":0,"y":-1,"z":0},"maxDist":32}` |

## Joueur

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `get_player_state` | oui | *(aucun)* | `{pos, rotation, health, maxHealth, food, saturation, xpLevel, xpProgress, gamemode, dimension, selectedSlot, onGround, name, effects[]}` | `{}` |
| `teleport` | non | `x,y,z` (number), `dimension?` (int), `yaw?`, `pitch?` (number) | `{success}` | `{"x":0,"y":100,"z":0}` |
| `set_gamemode` | non | `mode` (`survival`\|`creative`\|`adventure`\|`spectator`) | `{success, gamemode}` | `{"mode":"creative"}` |
| `set_spawn` | non | `x,y,z` (int) | `{success}` | `{"x":0,"y":64,"z":0}` |
| `get_inventory` | oui | *(aucun)* | `{slots:[{slot,item,count,damage,nbt?},...]}` (slots 0-40, voir adressage ci-dessous) | `{}` |
| `give_item` | non | `item` (string), `count` (int), `nbt?` (object) | `{success}` | `{"item":"minecraft:diamond","count":8}` |
| `clear_slot` | non | `slot` (int, voir adressage) | `{success}` | `{"slot":36}` |
| `equip_slot` | non | `slot` (int), `targetSlot` (int) | `{success}` | `{"slot":10,"targetSlot":37}` |

**Adressage des slots d'inventaire** (partage par `get_inventory`,
`clear_slot`, `equip_slot`, `transfer_items`) : `0-8` hotbar, `9-35`
inventaire principal, `36` offhand, `37` tete (casque), `38` torse
(plastron), `39` jambes, `40` pieds (bottes).

## Agent (jouer a la place du joueur)

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `walk_to` | non | `x, z` (number), `y` (number, indicatif), `timeoutMs?` (int, defaut 10000) | `{success, reason}` (`arrived`\|`timeout`\|`blocked`). **TODO(v2)** : ligne droite simplifiee, pas de vrai pathfinding | `{"x":100,"y":64,"z":50,"timeoutMs":15000}` |
| `look_at` | non | `x,y,z` (number) | `{success}` | `{"x":10,"y":70,"z":10}` |
| `jump` | non | *(aucun)* | `{success}` | `{}` |
| `start_sneaking` | non | `bool` (boolean) | `{success}` | `{"bool":true}` |
| `mine_block` | non | `x,y,z` (int) | `{success, block}` | `{"x":0,"y":64,"z":0}` |
| `place_block_at` | non | `x,y,z` (int, bloc de reference), `face` (`up`\|`down`\|`north`\|`south`\|`east`\|`west`) | `{success, pos}` ou `{success:false, reason:"occupied"}` | `{"x":0,"y":64,"z":0,"face":"up"}` |
| `use_item` | non | `hand?` (`main`\|`offhand`, defaut `main`) | `{success, actionResultType}` | `{"hand":"main"}` |
| `attack_entity` | non | `entityId` (int) | `{success}` | `{"entityId":123}` |

## Entites

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `list_entities` | oui | `radius` (number, max 64), `filter?` (string, sous-chaine du type) | `{entities:[{id,type,pos,health?,name?,isHostile},...]}` | `{"radius":16,"filter":"zombie"}` |
| `get_entity` | oui | `id` (int) | `{id,type,pos,rotation,health?,name?,isHostile,nbt}` | `{"id":123}` |
| `spawn_entity` | non | `type` (string), `x,y,z` (number), `nbt?` (object) | `{success, entityId}` | `{"type":"minecraft:cow","x":0,"y":64,"z":0}` |
| `kill_entity` | non | `id` (int) | `{success}` | `{"id":123}` |
| `follow_entity` | non | `id` (int) | `{success, reason}` (`close_enough`\|`duration_elapsed`\|`target_gone`). **TODO(v2)** : plafonne a 30s, pas de vrai pathfinding | `{"id":123}` |

## Chat & commandes

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `send_chat` | non | `message` (string) | `{success}` | `{"message":"Bonjour depuis Claude !"}` |
| `read_chat_history` | oui | `n?` (int, defaut 50) | `{messages:[string,...]}` | `{"n":20}` |
| `execute_command` | non | `command` (string, sans le `/`), `capture?` (boolean, defaut true) | `{success, commandsExecuted, output?:[string,...]}` | `{"command":"time set day"}` |
| `send_actionbar` | non | `message` (string) | `{success}` | `{"message":"Objectif atteint"}` |
| `send_title` | non | `title` (string), `subtitle?` (string), `fadeIn?, stay?, fadeOut?` (int, ticks) | `{success}` | `{"title":"Bienvenue","subtitle":"par Claude"}` |

## Monde global

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `get_world_info` | oui | *(aucun)* | `{time, day, isRaining, isThundering, difficulty, seed, dimensions:[int,...]}` | `{}` |
| `set_time` | non | `time` (string/number : ticks ou `day`\|`noon`\|`night`\|`midnight`), `dimension?` | `{success, time}` | `{"time":"night"}` |
| `set_weather` | non | `weather` (`clear`\|`rain`\|`thunder`), `duration?` (int, ticks), `dimension?` | `{success}` | `{"weather":"rain","duration":6000}` |

## Craft & conteneurs

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `list_recipes` | oui | `resultFilter?` (string), `ingredientFilter?` (string) | `{recipes:[{recipeId,result},...]}` (max 200) | `{"resultFilter":"diamond"}` |
| `craft_item` | non | `recipeId` (string), `count` (int) | `{success, crafted}` | `{"recipeId":"minecraft:diamond_block","count":1}` |
| `open_container` | non | `x,y,z` (int), `dimension?` | `{success}` (anime le couvercle pour un coffre) | `{"x":0,"y":64,"z":0}` |
| `read_container` | oui | `x,y,z` (int), `dimension?` | `{slots:[{slot,item,count,damage,nbt?},...]}` | `{"x":0,"y":64,"z":0}` |
| `transfer_items` | non | `fromSlot, toSlot` (int, adressage inventaire joueur), `count` (int) | `{success}` | `{"fromSlot":0,"toSlot":36,"count":1}` |

## Construction

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `generate_shape` | non | `shape` (`cube`\|`sphere`\|`cylinder`\|`pyramid`), `center` (`{x,y,z}`), `size` (`{width,height,depth}`), `block` (string), `hollow?` (boolean), `dimension?` | `{success, blocksPlaced}` | `{"shape":"sphere","center":{"x":0,"y":80,"z":0},"size":{"width":10,"height":10,"depth":10},"block":"minecraft:quartz_block"}` |
| `save_structure` | non | `from,to` (`{x,y,z}`), `name` (string) | **TODO(v2)** — erreur JSON-RPC "non implemente" | `{"from":{"x":0,"y":64,"z":0},"to":{"x":5,"y":68,"z":5},"name":"maison"}` |
| `load_structure` | non | `name` (string), `at` (`{x,y,z}`) | **TODO(v2)** — erreur JSON-RPC "non implemente" | `{"name":"maison","at":{"x":100,"y":64,"z":100}}` |

## Observation / visuel

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `screenshot` | oui | `maxSize?` (int, defaut 512) | `{format:"png", width, height, data (base64)}` | `{"maxSize":512}` |
| `get_camera_view` | oui | `maxDist?` (number, defaut 64) | `{rays:[{direction,hit,distance,block?,entityId?,entityType?}...], summary}` | `{"maxDist":32}` |
| `get_stats` | oui | *(aucun)* | `{stats:[{id,name,value},...]}` (non nulles uniquement) | `{}` |
| `get_achievements` | oui | *(aucun)* | `{achievements:[{id,done,percent},...]}` (Advancements, remplacent les achievements depuis 1.12) | `{}` |

## Redstone

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `interact_block` | non | `x,y,z` (int), `dimension?` | `{success}` | `{"x":0,"y":64,"z":0}` |
| `get_redstone_power` | oui | `x,y,z` (int), `dimension?` | `{power}` (0-15) | `{"x":0,"y":64,"z":0}` |

## Meta

| Outil | readOnly | Parametres | Retour | Exemple `arguments` |
|---|---|---|---|---|
| `ping` | oui | *(aucun)* | `{pong:true, version}` | `{}` |
| `list_tools_meta` | oui | *(aucun)* | `{tools:[{name,description,inputSchema,readOnly},...], count}` | `{}` |

---

## Codes d'erreur JSON-RPC utilises

| Code | Signification |
|---|---|
| `-32700` | Parse error (JSON invalide) |
| `-32600` | Invalid Request (enveloppe JSON-RPC invalide) |
| `-32601` | Method not found |
| `-32602` | Invalid params |
| `-32603` | Internal error |
| `-32000` | Erreur applicative generique |
| `-32001` | Outil inconnu |
| `-32002` | Outil refuse (readOnlyMode / whitelist / blacklist / confirmation refusee) |
