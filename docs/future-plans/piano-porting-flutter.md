# 🧭 PIANO - Porting di Snake a Flutter (Android + iOS)

> Piano a fasi per riscrivere Snake in **Flutter**, rendendolo disponibile sia su **Android** sia su **iOS**,
> mantenendo la parità di gameplay con la versione Kotlin attuale. Modellato sullo stile di
> [`PLANNING.md`](../../PLANNING.md). L'analisi di fattibilità che lo motiva è in
> [`analisi-fattibilita-flutter.md`](analisi-fattibilita-flutter.md).
>
> **Stato**: documento di pianificazione futura - il progetto **non è ancora iniziato**.
> **Lingua**: italiano su richiesta del proprietario (deroga consapevole all'inglese del `CLAUDE.md`, valida
> solo per questi documenti di pianificazione; gli artefatti del nuovo progetto saranno in inglese).
>
> Legenda: `[ ]` da fare · `[-]` in corso · `[x]` fatto

---

## 🎯 Obiettivo

Un Snake **cross-platform (Android + iOS)** scritto in Flutter/Dart, **a parità di gameplay** con la versione
Kotlin attuale (v0.9.x), con lo stesso livello di rifinitura visiva e sonora compatibilmente con la piattaforma.
Pubblicabile su **Google Play** e **App Store**.

## 🧰 Stack proposto

- **Linguaggio**: Dart
- **Framework**: Flutter (motore di rendering **Impeller**)
- **Rendering gameplay**: `CustomPainter` / `Canvas` (immediate-mode, mappa 1:1 con l'attuale `Canvas` di Compose)
- **Effetti GPU**: fragment shader `.frag` (GLSL/SkSL) via `FragmentProgram` + pacchetto `flutter_shaders`
- **Audio**: `just_audio` (musica con crossfade) + `audioplayers`/`soundpool` (SFX a bassa latenza)
- **Persistenza**: `shared_preferences` (semplice) oppure `Hive` (strutturata)
- **Vibrazione**: `HapticFeedback` (base, cross-platform) + pacchetto `vibration` (controllo fine)
- **Asset**: riuso diretto di font Orbitron, musica `.ogg`, SFX `.wav` dal progetto attuale
- **Stato/architettura**: a scelta (es. `ChangeNotifier`/`Riverpod`); il `GameViewModel` attuale è il riferimento

### Perché Dart e non Kotlin Multiplatform (KMP)
Integrare KMP dentro Flutter passa per FFI/platform channel: toolchain pesante, attriti con il modello reattivo
di Flutter, beneficio marginale per un gioco di queste dimensioni. La riscrittura del modello in Dart è più
lineare e, soprattutto, **riscrive anche i test**, rendendo la parità dimostrabile.

---

## ✅ Principi di esecuzione

1. **Ogni step deve essere compilabile e collaudabile** in isolamento. Mai lasciare il branch rotto.
2. Uno step = un commit (o un piccolo gruppo di commit correlati).
3. Dopo ogni step: `flutter analyze` + `flutter test` + smoke test su **Android e iOS**, poi spunta il todo.
4. Niente refactor opportunistici fuori dallo step corrente.
5. Il package del **modello** resta privo di import Flutter/`dart:ui`, così da restare **testabile** in puro Dart.
6. Solo asset con licenza libera; ogni asset aggiunto va registrato nei crediti con fonte e licenza.
7. **Verifica costante su entrambe le piattaforme**: ciò che funziona su Android va riprovato su iOS prima di
   spuntare lo step (specie shader, audio e vibrazione).

---

## 📐 Layout di repository proposto (nuovo progetto)

```
snake_flutter/
├── pubspec.yaml
├── lib/
│   ├── main.dart
│   ├── game/            # modello puro Dart (nessun import da dart:ui/Flutter)
│   ├── ui/              # widget, schermate, navigazione, tema
│   │   └── game/        # GameBoard (CustomPainter), controlli, overlay, particelle
│   ├── audio/           # musica, SFX, vibrazione (dietro interfacce)
│   └── data/            # persistenza preferenze + record
├── shaders/             # *.frag (background, glow, food_halo, [crt])
├── assets/
│   ├── fonts/           # Orbitron
│   └── audio/           # *.ogg, *.wav
├── test/                # port dei 16 file di unit test sul modello
├── android/  ios/       # progetti nativi generati da Flutter
└── docs/
```

---

## 🛣️ Roadmap

### Fase 0 - Fondamenta del progetto Flutter

- [ ] **Step 0.1** - Scaffolding `flutter create` (Android + iOS), `pubspec.yaml`, lint (`flutter_lints`),
      struttura cartelle. Verifica: `flutter run` avvia l'app vuota su emulatore Android **e** simulatore iOS.
- [ ] **Step 0.2** - Tema base Material 3, lock in portrait, edge-to-edge / safe area su entrambe le piattaforme.
      Verifica: schermo a tema, in portrait, su Android e iOS.
- [ ] **Step 0.3** - Import asset condivisi: font Orbitron, cartella audio, file shader (placeholder). Verifica:
      il font si applica a un testo di prova.
- [ ] **Step 0.4** - Setup test (`flutter test`) e CI minima (analyze + test). Verifica: la pipeline gira verde.

> 🎯 **Fine Fase 0**: app Flutter installabile, a tema, in portrait, che gira su Android e iOS.

### Fase 1 - Modello di gioco in Dart (parità con il modello Kotlin)

> **La parte a rischio più basso e fondante.** Si porta `game/` in Dart **prima della UI**, riscrivendo
> contestualmente i test. Finché i test non passano in parità, non si procede.

- [ ] **Step 1.1** - Tipi base: `Direction`, `Position`, `BoardDimensions`/`BoardScale` + `boardFor`,
      `Level`, `ControlScheme`, `GameMode`, `SnakeSpeed`. Verifica: compila; test sui tipi passano.
- [ ] **Step 1.2** - Sistema cibo: `FoodCategory`, `FoodSize`, `FoodTier`, `FoodEffect` (sealed), `FoodTable`
      con `roll` (gate temporali, probabilità, special). Verifica: port dei test cibo passa.
- [ ] **Step 1.3** - Stato: `GameState`/`GameStatus`, `Debris`, `ActiveEffect`/`EffectKind`, `GameEvent`.
      Verifica: i `copyWith` replicano il comportamento delle `data class`.
- [ ] **Step 1.4** - Motore: `GameEngine` (setup/start/tick/changeDirection/turn) con `Random` iniettabile e
      determinismo. Verifica: port di `GameEngineTest`, `RelativeTurnTest`, `SpecialFoodTest` passa.
- [ ] **Step 1.5** - Modalità e contenuti: `LevelsMode` (shape/hazard per livello, vite), `LevelHazards`
      (gate/teleport), simmetria ostacoli. Verifica: port di `LevelsModeTest`, `LevelShapesTest`,
      `ObstacleSymmetryTest`, `HazardTelegraphTest`, `LevelHazardsTest` passa.
- [ ] **Step 1.6** - Meta: `Achievement`, `Mission`, `Challenge`/`ChallengeModifier`, `ScoreKey`, `Skin`
      (regole di sblocco). Verifica: port di `AchievementTest`, `MissionTest`, `ChallengeTest`,
      `ScoreKeyTest`, `SkinTest`, `GameModeTest`, `BoardLayoutTest` passa.

> 🎯 **Fine Fase 1 (M1)**: motore di gioco completo in Dart, **tutti i 16 test in parità**. Nessuna UI ancora.

### Fase 2 - Spike tecnici di rischio (early de-risking)

> Prima di costruire la UI, si validano i punti incerti su **dispositivo fisico** (Android e iPhone).

- [ ] **Step 2.1** - Spike rendering: un `CustomPainter` che disegna una griglia + un quadrato in movimento a
      30/60 fps. Verifica: fluido su un device Android e un iPhone di fascia media.
- [ ] **Step 2.2** - Spike fragment shader semplice (un `BACKGROUND` ridotto) caricato da `.frag`. Verifica:
      compila e anima su Android **e** iOS/Impeller.
- [ ] **Step 2.3** - Spike shader con `sampler2D` (prototipo del CRT che campiona uno snapshot del child via
      `AnimatedSampler`). Verifica: funziona su iOS/Impeller **oppure** si documenta il limite e si conferma
      che il CRT resta confinato alla fase facoltativa finale.
- [ ] **Step 2.4** - Spike loop di gioco: `Ticker`/`AnimationController` che chiama `engine.tick()` a intervallo
      variabile, con interpolazione inter-tick. Verifica: movimento fluido, pacing corretto.

> 🎯 **Fine Fase 2**: le incognite tecniche (Canvas, shader, sampler, loop) sono validate su entrambe le
> piattaforme. Le scelte sul CRT sono prese in anticipo, non a lavoro finito.

### Fase 3 - Gameplay giocabile (renderer + input + loop)

- [ ] **Step 3.1** - `GameBoard` (`CustomPainter`): sfondo, griglia, ostacoli, cibi e serpente (testa più
      luminosa), con interpolazione inter-tick (port di `previousSnake` + `lerp`). Verifica: board centrata,
      serpente visibile e in movimento.
- [ ] **Step 3.2** - Loop di gioco nel controller di stato (equivalente del `GameViewModel`), a intervallo
      effettivo per tick. Verifica: il serpente avanza fino alla morte.
- [ ] **Step 3.3** - Input: swipe (default), schema **relativo a due pulsanti**, D-pad. Blocco inversione 180°
      lato motore. Verifica: il serpente si guida con tutti gli schemi.
- [ ] **Step 3.4** - Cibo, crescita, respawn, vanish, combo. Verifica: mangiare cresce; il combo sale; i cibi
      vecchi svaniscono.
- [ ] **Step 3.5** - Collisioni (muri, corpo, ostacoli, debris, gate) → game over; grace/coyote tick. Verifica:
      le morti scattano correttamente (coperto dai test del modello).
- [ ] **Step 3.6** - HUD punteggio + combo (overlay). Verifica: il punteggio si aggiorna a ogni cibo.

> 🎯 **Fine Fase 3 (M2)**: gioco giocabile su Android e iOS, ancora "spartano" sul piano visivo.

### Fase 4 - Rifinitura visiva (Canvas)

- [ ] **Step 4.1** - Serpente vettoriale: corpo a segmenti arrotondati / tubo continuo, testa con occhi
      orientati al moto (port delle due varianti di `SkinPalette`: `useGlow`, `segmentedBody`).
- [ ] **Step 4.2** - Sfondo a gradiente + griglia + bordo; ostacoli con smusso (ombra + luce).
- [ ] **Step 4.3** - Cibi animati (pulsazione, alone sui rari), glifo "?" per i mystery (text su Canvas).
- [ ] **Step 4.4** - Sistema particelle su Canvas: burst su mangiata (per colore/combo), implosione su shrink,
      fade su vanish, detonazione su esplosione.
- [ ] **Step 4.5** - Screen-shake (game over, terremoto, esplosione) + flash near-miss / hazard telegraph.
- [ ] **Step 4.6** - Blur in pausa (`BackdropFilter`) sopra la board congelata.

> 🎯 **Fine Fase 4**: parità visiva con la Fase 2/2.5 dell'app Kotlin, **senza** ancora gli shader GPU.

### Fase 5 - UI/UX e navigazione

- [ ] **Step 5.1** - Tipografia Orbitron + scala di stili riusabile.
- [ ] **Step 5.2** - Menu principale (titolo animato, Play, Settings) + navigazione tra schermate con transizioni
      in dissolvenza (equivalente di `Crossfade`).
- [ ] **Step 5.3** - Schermata impostazioni (livello, scala board, schema controlli, velocità, toggle hazard,
      frequenza special, riduzione movimento) persistite. Verifica: le scelte sopravvivono al riavvio.
- [ ] **Step 5.4** - Game over + record per `(mode, level, scale)`, contatore punteggio "rolling".
- [ ] **Step 5.5** - Gestione gesture di ritorno: back Android vs swipe-back iOS (comportamenti distinti).
- [ ] **Step 5.6** - Onboarding/tutorial al primo avvio + intro di brand (port adattato).

> 🎯 **Fine Fase 5 (M3)**: app con menu, impostazioni, navigazione e record, su entrambe le piattaforme.

### Fase 6 - Persistenza completa

- [ ] **Step 6.1** - Repository preferenze (port di `SettingsRepository`) su `shared_preferences`/`Hive`.
- [ ] **Step 6.2** - Record: per `(mode, level, scale)`, progressi Levels, best/streak Daily.
- [ ] **Step 6.3** - Meta persistente: achievement sbloccati, skin sbloccate, missioni completate per giorno.
      Verifica: sblocchi e streak si comportano come nell'app Kotlin.

### Fase 7 - Audio e vibrazione

- [ ] **Step 7.1** - Musica in loop con crossfade menu ↔ gameplay (`just_audio`, due sorgenti), lifecycle
      pause/resume, gestione audio focus/interruzioni. Verifica: la musica fa crossfade e si silenzia in background.
- [ ] **Step 7.2** - SFX a bassa latenza (mangiata per tier/combo, shrink, mystery, special, game over, UI, pausa).
      Verifica: nessun lag percepibile su Android e iOS.
- [ ] **Step 7.3** - Volumi Master/Music/SFX in impostazioni.
- [ ] **Step 7.4** - Vibrazione: mappatura eventi → feedback con `HapticFeedback`/`vibration`, **rimappata e
      semplificata su iOS**. Toggle in impostazioni. Verifica: feedback coerente, mai invadente (throttling near-miss).

> 🎯 **Fine Fase 7 (M4)**: app completa di audio e feedback aptico su entrambe le piattaforme.

### Fase 8 - Shader GPU (fragment shader)

> Esclude il CRT, isolato nella Fase 9 facoltativa.

- [ ] **Step 8.1** - `BACKGROUND` animato (glow alla deriva + vignettatura) come `.frag`, sotto menu e board.
      Verifica: animazione fluida su Android e iOS.
- [ ] **Step 8.2** - `GLOW` sulla testa del serpente. Verifica: alone pulsante coerente con le skin "glow".
- [ ] **Step 8.3** - `FOOD_HALO` sui cibi rari (maxi/mystery/huge). Verifica: anello/alone corretti.
- [ ] **Step 8.4** - Degrado controllato: se uno shader fallisse il caricamento su una piattaforma, fallback al
      rendering Canvas senza crash. Verifica: disattivando gli shader il gioco resta pienamente giocabile.

> 🎯 **Fine Fase 8 (M5)**: parità con la Fase 5 dell'app Kotlin (shader esclusi CRT) su Android e iOS.

### Fase 9 - Filtro CRT (FACOLTATIVA - da decidere a progetto avviato)

> ⚠️ **Fase a sé stante e opzionale.** Il CRT è l'effetto più rischioso da portare (fragment shader con
> `sampler2D` sotto Impeller su iOS) ed è praticamente mai usato. È un toggle in impostazioni: l'app è completa
> e pubblicabile **anche senza**. La decisione se realizzarla, rimandarla o **eliminarla** si prende quando il
> porting sarà avviato e gli spike della Fase 2 avranno chiarito il comportamento su iOS.
>
> Tre esiti possibili:
> 1. **Realizzarla** - se lo spike 2.3 ha dato esito positivo su iOS.
> 2. **Rimandarla** - rilasciare senza CRT e aggiungerlo in un aggiornamento successivo.
> 3. **Eliminarla** - rimuovere del tutto l'opzione CRT dal porting (impatto: nullo sul resto del gioco).

- [ ] **Step 9.1** - Port del filtro `CRT` (scanline + vignettatura) come fragment shader con `sampler2D` che
      campiona uno snapshot della board (`AnimatedSampler` o equivalente). Verifica: resa corretta su Android.
- [ ] **Step 9.2** - Validazione su iOS/Impeller su dispositivo fisico; toggle in impostazioni. Verifica:
      nessun artefatto/crash; se non soddisfacente, si applica l'esito 2 o 3 qui sopra.

### Fase 10 - Distribuzione (Android + iOS)

- [ ] **Step 10.1** - Identità app: `applicationId`/bundle id definitivi, versioni, nomi.
- [ ] **Step 10.2** - Icone e splash native via `flutter_launcher_icons` / `flutter_native_splash` (Android + iOS).
- [ ] **Step 10.3** - Android: build release (AAB), R8/shrink, firma + Play App Signing, scheda Play Store.
- [ ] **Step 10.4** - iOS: signing/provisioning, archive, App Store Connect, requisiti privacy (App Privacy).
- [ ] **Step 10.5** - Crediti asset (fonte + licenza) e documentazione utente aggiornata (in inglese).

> 🎯 **Fine Fase 10 (M6)**: build pubblicabili su **Google Play** e **App Store**.

---

## 🎯 Milestone

| Milestone | Contenuto |
|---|---|
| **M1** | Modello di gioco in Dart, 16 test in parità (Fase 1) |
| **M2** | Gioco giocabile, spartano, su Android + iOS (Fasi 2-3) |
| **M3** | UI/UX, menu, impostazioni, record (Fasi 4-5) |
| **M4** | Persistenza + audio + vibrazione complete (Fasi 6-7) |
| **M5** | Shader GPU (CRT escluso) (Fase 8) |
| **M6** | Pubblicabile su Play Store e App Store (Fase 10) |
| *(opz.)* | CRT (Fase 9) - da decidere |

---

## ⚠️ Rischi e mitigazioni

| Rischio | Mitigazione |
|---|---|
| Shader con sampler (CRT) instabili su iOS/Impeller | Spike 2.3 in anticipo; CRT isolato in fase facoltativa |
| Regressioni di gameplay nella riscrittura del modello | Port dei 16 test **prima** della UI (Fase 1 = gate) |
| Differenze aptiche iOS | Mappatura semplificata e dedicata su iOS (Step 7.4) |
| Performance del Canvas su device di fascia bassa | Spike 2.1 su device reale; budget particelle, niente lavoro per-frame inutile |
| Gesture di navigazione divergenti | Comportamenti distinti Android/iOS (Step 5.5) |
| Distribuzione iOS più rigida | Affrontata come fase dedicata (Step 10.4) con anticipo su signing/privacy |
