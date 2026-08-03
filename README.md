# Minecraft MCP

Mod Forge 1.12.2 qui embarque un serveur **MCP** (Model Context Protocol
d'Anthropic) directement dans le client Minecraft, pour permettre a un LLM
externe (Claude Desktop, agent custom...) d'interagir avec votre partie
**solo** via des outils exposes en JSON-RPC 2.0 / HTTP.

> **MCP** ici designe le *Model Context Protocol*. Ne pas confondre avec les
> mappings "MCP" (Mod Coder Pack) utilises par ForgeGradle pour ce meme projet.

## Sommaire

- [Prerequis](#prerequis)
- [Build & installation](#build--installation)
- [Configuration](#configuration)
- [Branchement a Claude Desktop](#branchement-a-claude-desktop)
- [Tester manuellement (curl)](#tester-manuellement-curl)
- [Limites connues (1.12.2 / Java 8)](#limites-connues-1122--java-8)
- [Securite](#securite)

## Prerequis

- **JDK 8** (obligatoire pour Forge 1.12.2 — ne fonctionne pas avec un JDK plus
  recent). Verifiez avec `java -version` et `javac -version`.
- **Gradle** n'a pas besoin d'etre installe : le wrapper ForgeGradle
  (`gradlew` / `gradlew.bat`) telecharge tout automatiquement.
- Une connexion internet lors du premier build (telechargement de Forge,
  Minecraft et des mappings MCP `stable_39`).

### Setup IntelliJ IDEA

```
git clone <votre-repo> minecraft-mcp
cd minecraft-mcp
gradlew.bat setupDecompWorkspace   # (Windows) ou ./gradlew setupDecompWorkspace
gradlew.bat idea                  # genere le projet IntelliJ
```

Ouvrez ensuite le dossier avec IntelliJ (Open > selectionner `build.gradle` ou
le dossier racine si vous utilisez l'import Gradle natif). Si vous preferez
l'import Gradle natif d'IntelliJ plutot que la tache `idea`, importez
directement `build.gradle` puis lancez `setupDecompWorkspace` depuis le panneau
Gradle. Tant que cette etape n'a pas ete faite, votre IDE affichera des erreurs
"cannot find symbol" sur toutes les classes Minecraft/Forge/Gson : c'est normal,
le classpath n'est pas encore resolu.

Pour lancer le client depuis IntelliJ : configuration de run generee
automatiquement `Minecraft Client` (apres `gradlew genIntellijRuns`).

## Build & installation

```
gradlew.bat build      # Windows
./gradlew build        # Linux/macOS
```

Le jar produit se trouve dans `build/libs/minecraft-mcp-0.1.0.jar`.

**Installation dans un client Minecraft Forge 1.12.2 existant :**

1. Installez Forge 1.12.2 (recommande : `14.23.5.2847`) si ce n'est pas deja
   fait.
2. Copiez `minecraft-mcp-0.1.0.jar` dans le dossier `mods/` de votre
   installation Minecraft (`.minecraft/mods/` ou equivalent selon votre
   launcher).
3. Lancez Minecraft avec le profil Forge 1.12.2. Le mod cree automatiquement
   son fichier de configuration au premier lancement.

## Configuration

Fichier genere : `config/mcmcp.cfg` (format Forge classique, cle=valeur avec
commentaires). Options principales :

| Option | Defaut | Description |
|---|---|---|
| `port` | `25567` | Port TCP d'ecoute du serveur HTTP MCP |
| `bindAddress` | `127.0.0.1` | Adresse d'ecoute. **Ne jamais mettre `0.0.0.0`** sans comprendre les risques (exposition reseau local) |
| `readOnlyMode` | `true` | Si true, bloque tous les outils marques `write` (ecriture) |
| `toolsWhitelist` | `[]` | Liste blanche de noms d'outils (snake_case). Vide = tous autorises (sous reserve de la blacklist) |
| `toolsBlacklist` | `[]` | Liste noire de noms d'outils. Prioritaire sur la whitelist |
| `requireConfirmation` | `false` | Si true, popup en jeu (accepter/refuser/timeout) avant chaque outil en ecriture |
| `confirmationTimeoutSeconds` | `30` | Delai avant refus automatique de la popup de confirmation |
| `logLevel` | `INFO` | Niveau de log applicatif (DEBUG, INFO, WARN, ERROR) |
| `bearerToken` | `""` (vide) | Si non vide, chaque requete HTTP doit fournir `Authorization: Bearer <token>` |
| `maxVolumeBlocks` | `200000` | Volume maximum (nombre de blocs) pour `fill_area`, `clone_area`, `generate_shape` |

Pour activer l'ecriture (indispensable pour que le LLM puisse agir sur le
monde), passez `readOnlyMode=false` dans `config/mcmcp.cfg` puis relancez le
jeu (ou utilisez le menu Mods > Config en jeu).

## Branchement a Claude Desktop

Le serveur MCP de ce mod parle **HTTP** (transport "Streamable HTTP", endpoint
unique `/mcp`), pas stdio. Selon la version de Claude Desktop que vous
utilisez, deux cas de figure :

**Cas 1 — Claude Desktop supporte nativement les serveurs MCP distants HTTP**
(`claude_desktop_config.json`) :

```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://127.0.0.1:25567/mcp"
    }
  }
}
```

**Cas 2 — Votre version de Claude Desktop ne gere que le transport stdio**
(config `command`/`args`) : utilisez un pont stdio -> HTTP tel que
`mcp-remote` (paquet npm) :

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:25567/mcp"]
    }
  }
}
```

> Le format exact de `claude_desktop_config.json` peut evoluer entre versions
> de Claude Desktop : verifiez la documentation Anthropic a jour si l'un des
> deux blocs ci-dessus ne fonctionne pas tel quel.

Redemarrez Claude Desktop apres modification du fichier de config. Les outils
du mod doivent apparaitre automatiquement (icone outils dans la conversation).

## Tester manuellement (curl)

Le serveur doit etre demarre (jeu lance, partie solo chargee). Remplacez le
port si vous l'avez change dans la config.

**initialize (handshake MCP) :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}'
```

**notifications/initialized (notification, pas de reponse attendue) :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

**tools/list :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

**ping (via tools/call) :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
```

**get_player_state :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_player_state","arguments":{}}}'
```

**get_block (lecture, toujours autorise meme en readOnlyMode) :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_block","arguments":{"x":0,"y":64,"z":0}}}'
```

**set_block (ecriture, necessite readOnlyMode=false) :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"set_block","arguments":{"x":0,"y":64,"z":0,"block":"minecraft:diamond_block"}}}'
```

**give_item :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"give_item","arguments":{"item":"minecraft:diamond","count":8}}}'
```

**Avec jeton bearer configure :**

```bash
curl -s http://127.0.0.1:25567/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer VOTRE_TOKEN" \
  -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
```

Voir [docs/TOOLS.md](docs/TOOLS.md) pour le tableau exhaustif de tous les
outils, leurs parametres et un exemple JSON-RPC pour chacun.

## Limites connues (1.12.2 / Java 8)

- **Java 8 uniquement** : pas de `var`, pas de records, pas de `switch`
  expressions, pas de text blocks. Les streams `java.util.stream` sont
  disponibles (Java 8) et utilises ponctuellement.
- **Un seul joueur** : le mod suppose une partie solo (un unique
  `EntityPlayerMP`). En multijoueur (serveur dedie), le mod ne demarre aucun
  serveur HTTP (voir `ServerProxy`).
- **`walk_to` et `follow_entity`** sont des implementations simplifiees
  (deplacement en ligne droite tick par tick, avec detection d'obstacle
  basique). Pas de vrai pathfinding (`PathNavigate`) : ces outils sont
  documentes TODO(v2) pour une version future avec evitement d'obstacles,
  sauts, portes, etc.
- **`save_structure` / `load_structure`** ne sont pas implementes en v1
  (TODO(v2) : format de fichier de structure, gestion NBT complete des tile
  entities et entites). Ils renvoient une erreur JSON-RPC explicite si
  appeles.
- **`set_block` / `fill_area`** n'exposent que la metadonnee (0-15) du bloc,
  pas les proprietes de blockstate modernes (orientation precise de
  redstone, forme d'escalier, etc.) — limitation du modele de bloc 1.12.2
  cote implementation choisie ici, pas du jeu lui-meme.
- **`get_achievements`** interroge en realite le systeme d'*Advancements*
  (les achievements au sens strict ont ete remplaces par les advancements
  des la version 1.12 de Minecraft) ; le nom de l'outil est conserve pour
  correspondre au brief d'origine.
- **Volume plafonne** : `fill_area`, `clone_area` et `generate_shape` sont
  limites par `maxVolumeBlocks` (config) pour eviter de figer le jeu sur une
  zone trop grande.
- **Confirmation en jeu** (`requireConfirmation=true`) ne gere qu'une seule
  demande a la fois ; un appel concurrent pendant qu'une popup est deja
  affichee est automatiquement refuse.

## Securite

- Le serveur ecoute par defaut uniquement sur `127.0.0.1` (jamais `0.0.0.0`
  par defaut).
- L'entete `Origin` des requetes HTTP est verifiee (CSRF) : une requete
  provenant d'un navigateur avec une origine differente de
  `localhost`/`127.0.0.1` est rejetee (403).
- Un jeton bearer optionnel peut etre exige (`bearerToken` dans la config).
- `readOnlyMode=true` par defaut : aucune ecriture possible tant que vous ne
  l'avez pas explicitement desactive.
- Toute exception levee par un outil est capturee et transformee en erreur
  JSON-RPC propre : le jeu ne doit jamais crasher a cause d'un appel MCP
  malforme.
