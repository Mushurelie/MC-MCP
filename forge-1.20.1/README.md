# Minecraft MCP - Forge 1.20.1

Portage 1.20.1 (Forge 47.x, Java 17) du mod. Voir le [README principal](../README.md)
pour le protocole, la configuration et le branchement a Claude.

## Build

```
# JDK 17 requis (JAVA_HOME pointant vers un JDK 17)
gradlew.bat build      # Windows
./gradlew build        # Linux/macOS
```

Jar produit : `build/libs/mc-mcp-forge-1.20.1-0.1.0.jar`, a copier dans `mods/`.

## Configuration

Fichier `config/mcmcp-common.toml` (memes cles que le README principal). Pour que le
LLM puisse modifier le monde : `readOnlyMode = false`.

## Specificites 1.20.1

- `set_block`, `fill_area`, `generate_shape` : etat de bloc au format commande,
  ex. `minecraft:oak_stairs[facing=north,half=top]` ou `minecraft:chest{Items:[...]}`.
- `save_structure` / `load_structure` : structures vanilla `.nbt` (blocs, NBT, entites),
  sauvees dans `saves/<monde>/generated/<namespace>/structures/`. `load_structure`
  charge aussi les structures de datapacks/mods (`namespace:nom`), avec rotation/miroir.
- `dimension` : `overworld`, `the_nether`, `the_end` ou tout identifiant de dimension
  de mod (`ad_astra:moon`).
- `screenshot` renvoie un bloc image MCP directement exploitable par le LLM.
- `execute_command` execute les commandes de mods, dont WorldEdit (`//set stone`,
  `//pos1 0,64,0`...). WorldEdit verifie les permissions du joueur : en solo, la
  partie doit autoriser les commandes (triches activees ou ouverte en LAN avec triches).
