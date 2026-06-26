# 🔍 Analisi di fattibilità - Porting di Snake a Flutter (Android + iOS)

> Documento di pianificazione futura. Descrive lo stato attuale del codice e analizza in dettaglio
> la fattibilità di riscrivere il gioco in **Flutter** per renderlo disponibile sia su **Android**
> sia su **iOS**. Il piano operativo a fasi è nel documento gemello
> [`piano-porting-flutter.md`](piano-porting-flutter.md).
>
> **Nota sulla lingua**: il `CLAUDE.md` impone repository in solo inglese. Questi documenti sono
> redatti in italiano su richiesta esplicita del proprietario, in quanto materiale di pianificazione
> personale e non artefatto del prodotto. Quando (e se) il porting partirà davvero, gli artefatti del
> nuovo progetto (codice, commit, doc del prodotto) torneranno a essere in inglese.

---

## 🎯 Verdetto in sintesi

**Il porting è fattibile, senza alcun blocco tecnico.** La difficoltà è **medio-alta** ed è concentrata
quasi tutta nella riscrittura della UI/rendering. Il fattore che rende l'operazione ragionevole, e non un
rifacimento al buio, è la disciplina architetturale già presente nel progetto Kotlin: un dominio di gioco
**puro, immutabile, deterministico e ampiamente testato**, con una UI che consuma **eventi**. Gran parte
del lavoro che di solito rende impossibile un porting pulito è già stato fatto, di fatto, dal disaccoppiamento
esistente.

---

## 📦 Stato attuale del codice

Circa **15.000 righe di Kotlin** complessive.

| Package | LOC (circa) | Ruolo | Dipendenze Android |
|---|---|---|---|
| `game/` | 2.955 | Modello + motore di gioco (regole pure) | **Nessuna** (Kotlin puro) |
| `ui/` | 8.689 | Compose: UI, navigazione, rendering Canvas, shader | Forti |
| `audio/` | 533 | Musica, effetti sonori, vibrazione | Forti |
| `data/` | 338 | Persistenza preferenze e record (DataStore) | Medie |
| `test/` | 2.391 | 16 file di unit test sul modello (JUnit) | Nessuna |

### Qualità del codice: alta

Aspetto decisivo ai fini del porting:

- **Separazione netta del dominio.** Il package `game/` è dichiaratamente privo di import Android/Compose e
  rispetta davvero il vincolo. `GameEngine.tick()` riceve uno `GameState` e ne restituisce **uno nuovo**
  (immutabilità totale, nessuna mutazione in-place), con `Random` iniettabile. Il motore è **deterministico**
  e già **testato a fondo** (16 file di test, 2.391 righe).
- **Eventi come confine UI.** Il motore emette `GameEvent` (`Ate`, `Died`, `Exploded`, `Teleported`,
  `LevelAdvanced`, ...). Il `GameViewModel` li traduce in effetti (particelle, audio, vibrazione, screen-shake).
  Questo confine evento → presentazione si reimplementa identico in qualsiasi tecnologia UI.
- **Astrazioni già pronte al disaccoppiamento.** `GameSfx` e `GameHaptics` sono interfacce con implementazione
  `None`: la logica non conosce i tipi audio di Android. Esattamente ciò che serve per un porting.
- Documentazione inline (KDoc) ricca e accurata.

**Difetti minori** (non bloccanti per il porting): `GameEngine.tick()` è lungo (~300 righe) ma coeso;
`GameViewModel` accentra molte responsabilità (loop, achievement, missioni, challenge, scoring). Il porting
è l'occasione naturale per spezzarli in unità più piccole, se si vuole.

---

## 🧭 Analisi componente per componente

### 1. Modello di gioco `game/` → riscrittura in Dart - ✅ rischio basso

Sono ~3.000 righe di logica pura. Due strade possibili:

1. **Kotlin Multiplatform (KMP)** per riusare il codice così com'è. **Sconsigliato**: integrare KMP dentro
   Flutter passa per FFI / platform channel, introduce una toolchain pesante e si sposa male con il modello
   reattivo di Flutter. Il gioco non giustifica questa complessità.
2. **Riscrittura in Dart** (**consigliata**). La logica usa solo `data class`, `enum`, `List`/`Set` e `Random`:
   tutto ha equivalenti Dart diretti (`copyWith`, `sealed class`, collezioni immutabili, `dart:math` `Random`
   con seed).

Vantaggio decisivo: **i 16 file di test si riscrivono in Dart insieme al modello**, quindi la parità di
comportamento con l'attuale gioco è **dimostrabile**, non sperata. È la parte a rischio più basso dell'intero
porting e va affrontata per prima.

### 2. UI e rendering Compose → Flutter `CustomPainter` - ⚠️ rischio medio (il grosso del lavoro)

Circa 8.700 righe da riscrivere. La mappatura concettuale è buona:

- Compose `Canvas`/`DrawScope` → `CustomPainter`/`Canvas` di Flutter (entrambi immediate-mode su Skia).
- L'interpolazione inter-tick (`previousSnake` + `lerp` per movimento fluido) si replica con
  `Ticker`/`AnimationController`.
- `Crossfade`/`AnimatedContent`, blur in pausa, screen-shake, sistema di particelle: tutti hanno equivalenti
  Flutter (`AnimatedSwitcher`, `BackdropFilter`, `Transform`, particelle gestite a mano nel painter).
- Navigazione state-based (`ui/App.kt`) → `Navigator` o un semplice controller di stato.
- `PredictiveBackHandler` (gesture back Android) → su iOS non esiste; va gestita la swipe-back nativa iOS in modo diverso.

È lavoro **voluminoso ma meccanico**. Nessuna funzionalità è irrealizzabile in Flutter.

### 3. Shader AGSL → fragment shader Flutter - ⚠️ rischio alto (insidia tecnica principale)

Ci sono **4 shader** in **AGSL** (`ui/game/Shaders.kt`):

| Shader | Funzione | Difficoltà di porting |
|---|---|---|
| `BACKGROUND` | Sfondo animato (glow alla deriva + vignettatura) | Diretta |
| `GLOW` | Alone pulsante della testa del serpente | Diretta |
| `FOOD_HALO` | Anello/alone pulsante sui cibi rari | Diretta |
| `CRT` | Filtro scanline + vignettatura sul layer board | **Delicata** |

Flutter supporta i fragment shader dalla versione 3.7 tramite `FragmentProgram` (file `.frag` in GLSL,
pacchetto `flutter_shaders`). AGSL e lo shading language di Flutter derivano entrambi da **SkSL**: sono
**molto vicini ma non identici**. Serve una traduzione di sintassi (`half4`→`vec4`, dichiarazione di uniform e
sampler, `main(float2 fragCoord)` → output `fragColor` con `FlutterFragCoord()`). I primi tre shader (uniform
`float`/colore, output di colore) sono **porting diretti**.

L'**unico punto davvero delicato** è il filtro **`CRT`**: usa `uniform shader content` con
`RenderEffect.createRuntimeShaderEffect(..., "content")`, cioè uno shader che **campiona il layer già
renderizzato**. In Flutter si realizza con un `sampler2D` alimentato da uno snapshot del child (es. l'helper
`AnimatedSampler` di `flutter_shaders`). È fattibile, ma è la cosa che richiede più collaudo, **specialmente
su iOS con il motore Impeller**: i fragment shader con sampler vanno testati su dispositivo fisico prima di
darli per acquisiti.

**Decisione di progetto richiesta**: il CRT è già un effetto **opzionale** (toggle in Settings) e praticamente
mai usato. Per ridurre il rischio del porting è isolato in una **fase finale a sé stante e facoltativa**
(vedi il piano), così potrà essere realizzato, rimandato o **escluso del tutto** senza impatto sul resto.

### 4. Audio → `just_audio` / `audioplayers` - ✅ rischio basso

`MediaPlayer` (musica in loop con crossfade su due player + audio focus) e `SoundPool` (SFX a bassa latenza)
si rimpiazzano con `just_audio` (musica) e `audioplayers`/`soundpool` (effetti). La logica di crossfade è ~30
righe riscrivibili. Gli asset (`.ogg`, `.wav`) sono **già portabili senza conversione**. L'audio focus di
Android ha un equivalente cross-platform meno granulare ma sufficiente.

### 5. Persistenza DataStore → `shared_preferences` / `Hive` - ✅ rischio basso

`SettingsRepository` salva preferenze e highscore per chiave `(mode, level, scale)`, più achievement, skin,
streak e missioni. `shared_preferences` copre i casi semplici; `Hive` è preferibile se si vuole struttura.
Nota: i salvataggi attuali **non migrano** verso il nuovo progetto, ma trattandosi di nuove installazioni su
piattaforme nuove non è un problema reale.

### 6. Vibrazione/haptics → `HapticFeedback` / pacchetto `vibration` - ⚠️ rischio medio su iOS

`HapticController` usa `VibrationEffect`/`VibratorManager` con effetti predefiniti (`EFFECT_TICK`, ecc.). In
Flutter: `HapticFeedback` copre i casi base cross-platform; per il controllo fine c'è il pacchetto `vibration`.
**iOS espone un'API aptica diversa e più limitata** (Taptic Engine): alcune sfumature andranno semplificate o
rimappate, non riprodotte 1:1.

### 7. Splash, icone, font, manifest

Splash screen, adaptive icon (Android) e icone iOS si rifanno con la pipeline Flutter
(`flutter_native_splash`, `flutter_launcher_icons`). Il font **Orbitron** è riusabile così com'è. Il lock in
portrait si imposta lato Flutter (per iOS anche in `Info.plist`).

---

## 📊 Stima dello sforzo e rischio

| Componente | Rischio | Sforzo relativo |
|---|---|---|
| Modello `game/` + test in Dart | Basso | ~20% |
| UI / rendering su Canvas | Medio | ~45% |
| Shader (escluso CRT) | Medio-alto | ~7% |
| Filtro CRT (opzionale, su iOS) | **Alto** | ~3% |
| Audio | Basso | ~8% |
| Persistenza | Basso | ~5% |
| Vibrazione (iOS) | Medio | ~5% |
| Splash / icone / setup iOS | Basso | ~7% |

---

## ⚠️ Rischi specifici per iOS

Da collaudare per primi, su dispositivo fisico:

1. **Fragment shader con sampler** (il CRT) sotto **Impeller**: storicamente l'area con più spigoli.
2. **API aptica ridotta** rispetto ad Android: aspettarsi un feedback semplificato.
3. **Gesture di navigazione native** diverse (niente predictive-back; c'è la swipe-back iOS).
4. **Distribuzione**: App Store Connect, provisioning/signing, requisiti privacy. Nessun blocco, ma flusso
   diverso e più rigido del Play Store.

Nessuno di questi è un blocco. Sono i punti su cui investire collaudo all'inizio, non a UI finita.

---

## ✅ Strategia consigliata (bottom-up, sempre verificabile)

1. **Prima il modello + i test in Dart.** Finché i test non passano in parità, niente UI. Questo congela la
   "fonte di verità" del gameplay e azzera il rischio di regressioni invisibili.
2. **Spike sugli shader fin da subito** (in particolare il CRT con sampler) su iPhone reale con Impeller: meglio
   scoprire i limiti all'inizio che a lavoro finito.
3. **Ricostruzione della UI** sopra il modello già verificato, schermata per schermata, a partire dal `GameBoard`.
4. **Audio, persistenza, vibrazione, splash** come ultimo strato.
5. **CRT come fase facoltativa finale**, da decidere a progetto avviato.

Il dettaglio operativo, suddiviso in fasi e step verificabili sul modello di `PLANNING.md`, è in
[`piano-porting-flutter.md`](piano-porting-flutter.md).
