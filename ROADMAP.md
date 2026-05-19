# 🗺️ ROADMAP — Migrazione a Godot 4

Piano di evoluzione dello Snake Game dall'attuale prototipo WinForms (GDI+) a un gioco con grafica e produzione di livello "pro" basato su **Godot 4 (.NET / C#)**.

---

## 🎯 Obiettivo

Trasformare lo Snake amatoriale attuale in un gioco visivamente curato, cross-platform (Windows / Linux / macOS / Web), con grafica animata, particelle, shader, audio, menu e replayability — mantenendo C# come linguaggio.

## 🧰 Stack scelto

- **Engine**: [Godot 4.x — .NET edition](https://godotengine.org/) (MIT, free)
- **Linguaggio**: C# (.NET 8+, come richiesto da Godot .NET)
- **IDE consigliato**: VS Code o Rider (con estensione Godot)
- **Asset**: solo licenze free (CC0 / CC-BY / MIT). Fonti suggerite: [Kenney.nl](https://kenney.nl), [OpenGameArt](https://opengameart.org), [itch.io free assets](https://itch.io/game-assets/free)

### Perché Godot e non altro
- Supporta C# nativo → riuso delle competenze esistenti
- Editor 2D maturo: TileMap, AnimationPlayer, GPUParticles2D, shader GLSL-like
- Export Web (HTML5) → pubblicabile su itch.io / GitHub Pages
- Open source, nessuna royalty, community ampia

Alternative scartate: MonoGame (no editor → più lavoro), Unity (licensing restrittivo, overkill), Stride (community piccola).

---

## 📐 Struttura target del repo

```
snake-game/
├── src/SnakeGame/         # Legacy WinForms (rimane finché parità non raggiunta)
├── godot/                 # Nuovo progetto Godot
│   ├── project.godot
│   ├── scenes/
│   ├── scripts/
│   ├── assets/
│   │   ├── sprites/
│   │   ├── audio/
│   │   ├── fonts/
│   │   ├── shaders/
│   │   └── CREDITS.md
│   └── themes/
├── legacy/                # Destinazione finale del WinForms (Fase 7)
├── .github/workflows/     # CI export (Fase 7)
├── CLAUDE.md
├── README.md
└── ROADMAP.md
```

---

## ✅ Principi di esecuzione

1. **Ogni step deve essere compilabile e testabile** in isolamento. Mai lasciare il branch in stato rotto.
2. Uno step = un commit (o un piccolo gruppo di commit logici).
3. Dopo ogni step: build + apertura editor + smoke test manuale, poi tick sulla todo.
4. Niente refactor opportunistici fuori dallo step in corso.
5. Asset solo free: ogni asset aggiunto va annotato in `godot/assets/CREDITS.md` con fonte e licenza.

---

## 🛣️ Roadmap

### Fase 0 — Setup & fondamenta

- [ ] **Step 0.1** — Aggiungere `godot/` con un progetto Godot 4.x .NET vuoto (`project.godot`, `.csproj`, `.gitignore` per `.godot/`, `.mono/`, `bin/`, `obj/`). Verifica: l'editor apre il progetto senza errori.
- [ ] **Step 0.2** — Creare scena `Main.tscn` con un `Node2D` root e uno sfondo `ColorRect` a tutto schermo. Verifica: `godot --path godot` mostra una finestra colorata.
- [ ] **Step 0.3** — Configurare display: risoluzione base 1280×720, stretch mode `viewport`, aspect `keep`. Verifica: ridimensionando la finestra il contenuto scala correttamente.
- [ ] **Step 0.4** — Aggiungere `godot/assets/CREDITS.md` (vuoto, template).

### Fase 1 — Core gameplay (parità funzionale col WinForms)

- [ ] **Step 1.1** — Script `GameBoard.cs` con costanti `WIDTH`/`HEIGHT`/`CELL_SIZE`. Disegnare la griglia di gioco come sfondo `ColorRect` con bordo. Verifica: la board è visibile e centrata.
- [ ] **Step 1.2** — `Snake.cs`: lista di `Vector2I`, rendering del corpo come `ColorRect` riposizionati ogni tick. Niente movimento ancora. Verifica: serpente di 3 segmenti visibile al centro.
- [ ] **Step 1.3** — Game loop con `Timer` Godot a 150ms: movimento del serpente in una direzione. Verifica: si muove costantemente in alto fino al bordo.
- [ ] **Step 1.4** — Input frecce + blocco inversione 180°. Verifica: si controlla con le frecce.
- [ ] **Step 1.5** — Spawn del cibo base (1 sola tipologia, `ColorRect` rosso). Crescita serpente al consumo. Verifica: mangiare allunga e rispawna.
- [ ] **Step 1.6** — Collisioni: muri, corpo, ostacoli. Stato `GameOver` che ferma il timer. Verifica: il serpente muore correttamente nei tre casi.
- [ ] **Step 1.7** — HUD: `Label` Score in alto. Verifica: lo score si aggiorna mangiando.
- [ ] **Step 1.8** — Pausa (Spazio/P) e Restart (R). Verifica: i tasti funzionano e overlay "PAUSA" appare.
- [ ] **Step 1.9** — Porting dei **5 livelli di difficoltà** (velocità + numero ostacoli). UI laterale con `OptionButton` o `Button` group. Verifica: cambiando livello cambia velocità e ostacoli.
- [ ] **Step 1.10** — Porting delle **5 dimensioni board** (Tascabile → Infinito). Verifica: cambiando dimensione la board si ridimensiona prima della partita.
- [ ] **Step 1.11** — Porting dei **7 tipi di cibo** (Green/Red/Gold/Blue/Mega*) con le stesse probabilità e bonus dell'originale. Verifica: nel tempo compaiono tutti i tipi.

> 🎯 **Fine Fase 1**: parità funzionale con WinForms. Il gioco è giocabile ma graficamente ancora "blocky".

### Fase 2 — Visual polish

- [ ] **Step 2.1** — Sostituire il serpente `ColorRect` con uno `Sprite2D` per ogni segmento usando un tileset (head/body/curve/tail). Gestire orientamento e curve in base ai vicini. Verifica: il serpente ha sprite proprie e curve disegnate correttamente.
- [ ] **Step 2.2** — `TileMap` di sfondo con texture (erba, dungeon, neon — sceglierne uno). Verifica: la board ha un fondo coerente, non più nero piatto.
- [ ] **Step 2.3** — Sprite animate per i cibi (`AnimatedSprite2D`, 4-6 frame di bobbing/sparkle). Verifica: i cibi pulsano/brillano in idle.
- [ ] **Step 2.4** — Ostacoli con sprite (rocce / muri) anziché rect grigio. Verifica: la board sembra "ambientata".
- [ ] **Step 2.5** — **Smooth movement**: interpolare la posizione dei segmenti via `Tween` tra un tick e l'altro (il movimento logico resta a griglia, ma visivamente è fluido). Verifica: il serpente non "scatta" più tra le celle.
- [ ] **Step 2.6** — `GPUParticles2D` "burst" al momento di mangiare un cibo (colore in base al tipo). Verifica: ogni boccone produce particelle.
- [ ] **Step 2.7** — Screen shake (Camera2D + tween) alla collisione di game over. Verifica: alla morte la camera trema brevemente.
- [ ] **Step 2.8** — Effetto "trail" / glow sulla testa del serpente (luce o particella scia). Verifica: la testa è visivamente distinta dal corpo.

### Fase 3 — UI / UX pro

- [ ] **Step 3.1** — Importare un font custom (es. *Press Start 2P* o *VT323*, font free) e creare un `Theme` Godot riusabile. Verifica: tutta la UI usa il nuovo font.
- [ ] **Step 3.2** — Scena `MainMenu.tscn` con titolo animato, pulsanti Play / Opzioni / Esci, sfondo animato (serpente demo che si muove dietro). Verifica: avviando il gioco si parte dal menu.
- [ ] **Step 3.3** — Scena `Settings.tscn`: scelta livello, dimensione board, volume musica/SFX. Persistenza su `user://settings.cfg`. Verifica: chiudere/riaprire mantiene le scelte.
- [ ] **Step 3.4** — Overlay di **pausa** con shader di blur sullo sfondo. Verifica: in pausa lo sfondo è sfocato.
- [ ] **Step 3.5** — Scena `GameOver.tscn` con score finale, **highscore** persistente per (livello, dimensione), pulsanti Riprova / Menu. Verifica: highscore sopravvive ai riavvii.
- [ ] **Step 3.6** — HUD score animato (counter che si incrementa progressivamente con `Tween`, non a scatti). Verifica: lo score "rolla" come negli arcade.
- [ ] **Step 3.7** — Transizioni di scena con fade-in/fade-out. Verifica: nessun cambio scena "secco".

### Fase 4 — Audio

- [ ] **Step 4.1** — Aggiungere una traccia musicale di sottofondo loop (CC0). `AudioStreamPlayer` con autoplay. Verifica: musica in background.
- [ ] **Step 4.2** — SFX: mangia (varianti per tipo cibo), game over, click UI, pausa. Verifica: ogni evento ha il suo suono.
- [ ] **Step 4.3** — Bus audio separati Master/Music/SFX + slider in Settings. Verifica: i volumi si regolano indipendentemente.
- [ ] **Step 4.4** — Crossfade della musica tra menu e gameplay. Verifica: il passaggio è morbido.

### Fase 5 — Shader & FX

- [ ] **Step 5.1** — Shader di **glow** sulla testa del serpente (additive blending o `CanvasItem` shader). Verifica: la testa emette luce.
- [ ] **Step 5.2** — Shader pulsante sui cibi rari (Gold/Mega) con outline luminoso. Verifica: i cibi rari sono evidentemente più "preziosi".
- [ ] **Step 5.3** — Shader di sfondo (gradient animato, parallax di stelle, o caustiche acqua). Verifica: lo sfondo è vivo.
- [ ] **Step 5.4** — (Opzionale) filtro CRT/scanline come opzione attivabile in Settings. Verifica: si può attivare/disattivare in tempo reale.

### Fase 6 — Contenuti e replayability

- [ ] **Step 6.1** — Sistema di **skin** (Classic / Neon / Retro / Pixel). Skin = set di sprite + tileset + palette + opz. shader. Selezione in Settings. Verifica: cambiando skin tutto si aggiorna.
- [ ] **Step 6.2** — Power-up temporanei: speed boost, ghost mode (attraversa muri per 3s), magnete (attira cibo). Spawn raro. Verifica: i power-up appaiono e funzionano con icona HUD timer.
- [ ] **Step 6.3** — Highscore table per (livello × dimensione) mostrata in un menu "Records". Verifica: top 5 punteggi visibili e persistenti.
- [ ] **Step 6.4** — Achievement locali (es. "100 cibi mangiati", "sopravvivi 5 min a Leggenda"). Verifica: si sbloccano e sono visibili in un pannello.
- [ ] **Step 6.5** — Modalità extra: "Endless" (no ostacoli, board cresce), "Time Attack" (60s). Verifica: selezionabili da menu, hanno regole proprie.

### Fase 7 — Distribuzione & cleanup

- [ ] **Step 7.1** — Configurazione export presets in Godot: Windows, Linux, Web (HTML5). Verifica: si genera un build funzionante per ogni target.
- [ ] **Step 7.2** — Workflow GitHub Actions che builda gli export ad ogni tag `v*` e pubblica su Releases. Verifica: creando un tag esce un release con i binari.
- [ ] **Step 7.3** — Build Web pubblicata su GitHub Pages (o itch.io). Verifica: si gioca da browser.
- [ ] **Step 7.4** — Spostare `src/SnakeGame/` in `legacy/SnakeGame/` e aggiornare README + CLAUDE.md per puntare alla versione Godot come "principale". Verifica: la documentazione riflette il nuovo stato.
- [ ] **Step 7.5** — Aggiornare `README.md` con screenshot/GIF della nuova versione, istruzioni Godot, link al gioco web. Verifica: nuovo utente capisce in 30 secondi cosa fare.

---

## 🧪 Definition of Done (per ogni step)

- Compila senza warning nuovi
- Apre nell'editor Godot senza errori
- Smoke test manuale del flusso impattato OK
- `godot/assets/CREDITS.md` aggiornato se aggiunti asset
- Commit con messaggio in italiano nel formato: `feat(godot): step X.Y — <descrizione>` o `fix`/`docs`/`chore`

## 📊 Milestone di alto livello

| Milestone | Step | Risultato |
|---|---|---|
| **M1 — Parità** | Fine Fase 1 | Snake giocabile in Godot, stesso gameplay del WinForms |
| **M2 — Bello** | Fine Fase 2-3 | Grafica curata, UI con menu, look professionale |
| **M3 — Vivo** | Fine Fase 4-5 | Audio + shader, sensazione "arcade premium" |
| **M4 — Profondo** | Fine Fase 6 | Skin, power-up, achievement, replayability |
| **M5 — Pubblico** | Fine Fase 7 | Build multi-piattaforma + web, legacy archiviato |
