# CLAUDE.md

Guida per Claude Code (e altri agenti AI) quando lavorano in questo repository.

## Panoramica del progetto

Snake Game classico, nato come progetto didattico in **C# / .NET 10 / Windows Forms** per esercitarsi con event-driven programming, game loop e rendering GDI+.

Il repository sta migrando verso **Godot 4 (.NET / C#)** per portare il progetto da livello amatoriale a livello "pro" con grafica accattivante, effetti, audio e build multi-piattaforma. La migrazione segue lo schema descritto in `ROADMAP.md`.

## Stato corrente

- **Versione attiva (legacy)**: WinForms in `src/SnakeGame/`
  - Solo Windows, target `net10.0-windows`
  - Tutta la logica + UI + rendering centralizzati in `SnakeForm.cs` (~740 righe)
  - Rendering 2D con `Graphics`/GDI+ su `PictureBox` (rettangoli pieni, ellissi, poligoni stella per il cibo Blue)
  - Game loop tramite `System.Windows.Forms.Timer`
  - Input via override di `ProcessCmdKey` (intercetta frecce/spazio/P/R prima dei controlli)
- **Versione target (in arrivo)**: Godot 4 (.NET) in `godot/` — vedi ROADMAP

## Comandi

### Build & run (versione WinForms, solo Windows)
```powershell
cd src/SnakeGame
dotnet build
dotnet run
```

Non esistono test automatici, linter o CI in questo momento.

### Build & run (versione Godot — disponibile dalla Fase 1 della roadmap)
```bash
# Apri il progetto con l'editor Godot 4.x (.NET edition)
godot --path godot --editor

# Run headless da CLI
godot --path godot
```

## Architettura (legacy WinForms)

File principale: `src/SnakeGame/SnakeForm.cs`.

Tutto è in un'unica classe `SnakeForm`. Le aree logiche, anche se non separate in file, sono:

- **Configurazione livelli e board size**: array statici `LevelNames`, `BoardSizes`, e i metodi `GetLevelObstacleCount`/`GetLevelSpeed`.
- **Stato del gioco**: `_snake` (lista di `Point`), `_obstacles`, `_foods` (lista di `FoodItem`), `_currentDirection`, flag `_isGameOver`/`_isPaused`, `_score`, `_pendingGrowth`.
- **UI WinForms** (`InitializeGameUI`): pannello laterale con radio button livello/dimensione, score label, bottoni Start/Pause, `PictureBox` canvas.
- **Game loop**: `_gameTimer.Tick` → `MoveSnake()` → `CheckCollision()` → `_gameCanvas.Invalidate()`.
- **Tipi di cibo** (`FoodType`): Green/Red/Gold/Blue + varianti Mega. Probabilità in `SpawnFood` via roll percentuale. Il cibo Blue dà un incremento random 2-24.
- **Rendering** (`GameCanvas_Paint`): disegna cibo (ellissi o stella per Blue), ostacoli (rect grigi), snake (rect verdi, testa Chartreuse), overlay di pausa.
- **Input** (`ProcessCmdKey`): frecce → cambio direzione (con blocco 180°), Space/P → pausa, R → restart.

## Convenzioni e linee guida per lavorare su questo repo

- **Lingua**: README, UI e commenti utente sono in **italiano**. Mantieni l'italiano per stringhe visibili all'utente, commit message e PR.
- **Naming**: campi privati con underscore prefix (`_snake`, `_score`); PascalCase per metodi e tipi; constanti SCREAMING o PascalCase a seconda del contesto esistente.
- **Branch**: lo sviluppo della migrazione Godot avviene su `claude/snake-godot-migration-vL9R6`. Non pushare su `main` senza permesso esplicito.
- **Roadmap-driven**: prima di aggiungere una nuova funzionalità, verifica in `ROADMAP.md` se rientra in uno step esistente e completa gli step in ordine. Ogni step della roadmap deve restare **compilabile e testabile** a sé stante.
- **Niente refactor "a sorpresa"** sul WinForms legacy: la migrazione affianca il progetto Godot senza riscrivere il vecchio se non strettamente necessario. Quando la versione Godot raggiunge parità di feature (fine Fase 1), il WinForms si archivia in `legacy/`.
- **Asset**: nuovi asset grafici/audio devono essere free e con licenza compatibile (MIT/CC0/CC-BY). Documenta la fonte e la licenza in `godot/assets/CREDITS.md`.

## File chiave

| Path | Ruolo |
|---|---|
| `src/SnakeGame/SnakeForm.cs` | Tutta la logica + rendering della versione WinForms |
| `src/SnakeGame/SnakeGame.csproj` | Progetto .NET 10 Windows |
| `README.md` | Documentazione utente in italiano |
| `ROADMAP.md` | Piano di migrazione a Godot, step-by-step |
| `.github/copilot-instructions.md` | Istruzioni per Copilot (allineate a questo file) |
