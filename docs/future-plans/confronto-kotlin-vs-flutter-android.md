# ⚖️ Confronto - Kotlin + Jetpack Compose vs Flutter (solo Android)

> Documento di pianificazione futura. Risponde alla domanda: **per un gioco come questo Snake, con
> obiettivo esclusivamente Android, è stata una buona scelta Kotlin + Jetpack Compose o sarebbe stato
> meglio Flutter?** Complementare a [`analisi-fattibilita-flutter.md`](analisi-fattibilita-flutter.md)
> (che valuta il porting cross-platform) e a [`piano-porting-flutter.md`](piano-porting-flutter.md).
>
> **Nota sulla lingua**: italiano su richiesta del proprietario, in deroga consapevole alla regola
> "solo inglese" del `CLAUDE.md`, valida per questi documenti di pianificazione personale.

---

## 🎯 Risposta in sintesi

- **Per un obiettivo solo-Android, Kotlin + Jetpack Compose è stata la scelta giusta**, quasi da manuale:
  è lo stack nativo ufficiale, senza runtime intermedi, con accesso diretto a ogni API di piattaforma.
- **Flutter sarebbe stato preferibile solo se l'obiettivo iOS fosse stato noto fin dall'inizio.** Il suo
  valore principale (un codebase, due piattaforme) si materializza soltanto quando aggiungi iOS.
- **Sul singolo target Android, il nativo Compose sfrutta meglio hardware e piattaforma**: footprint minore,
  avvio più rapido, integrazione di sistema e shader/haptics di prima classe. Sulla fluidità grafica 2D i
  due sono sostanzialmente alla pari.

In breve: la scelta originale era ottima **per ieri** (Android-only); Flutter ha senso **per domani**
(Android + iOS). È esattamente il bivio attuale.

---

## 1. È stata una buona scelta Kotlin + Compose per solo-Android?

**Sì.** Se il target è esclusivamente Android, Kotlin + Compose è lo stack *nativo* ufficiale: nessun layer
intermedio, nessun runtime di terze parti da spedire, accesso diretto e immediato a ogni API di piattaforma.
Per un gioco che vive di:

- rendering immediate-mode su `Canvas`,
- **shader AGSL** (`RuntimeShader`) per glow / sfondo / CRT,
- haptics fini (`VibrationEffect` / `VibratorManager`),
- splash screen API, edge-to-edge, predictive-back, adaptive icon, DataStore,

…queste cose vengono usate **come sono pensate per essere usate**, senza wrapper. In più, il progetto ha
prodotto un modello di dominio puro e testato (16 file di test) che è proprio ciò che rende possibile un
eventuale porting. Scelta corretta ed eseguita bene.

Unico distinguo: era ottimale **dato** l'obiettivo Android-only. Conoscendo da subito il target iOS, la
valutazione sarebbe stata diversa.

## 2. Sarebbe stato meglio Flutter per questo tipo di gioco?

**Per Android-only, no.** Flutter avrebbe aggiunto un motore di rendering e un runtime Dart sopra la
piattaforma, senza dare nulla in cambio sul singolo target: niente utenti iOS da servire, quindi il beneficio
chiave del cross-platform non si concretizza. Si pagherebbe il costo senza incassarne il vantaggio.

Da tenere presente cosa Flutter **non** è: un game engine. Per uno Snake 2D va benissimo (Canvas + fragment
shader bastano), ma né Flutter né Compose sono Unity/Godot. Per questo tipo di gioco entrambi sono adeguati e
nessuno dei due offre un vantaggio "da motore di gioco".

## 3. Quale sfrutta meglio hardware e piattaforma?

**Kotlin + Compose, in modo netto, su Android.** Motivi strutturali:

- **Piattaforma**: Compose *è* Android. Accesso first-class e a costo zero a ogni API (AGSL, haptics
  granulari, audio focus, lifecycle, Play Services). Flutter raggiunge le stesse cose via plugin/platform
  channel - un ponte - spesso con un sottoinsieme più piccolo e in ritardo rispetto alle novità Android.
- **Hardware / rendering**: sia Compose sia Flutter (con il motore **Impeller**) girano su GPU. Per un gioco
  2D **entrambi sono fluidi**. La differenza pratica: Compose disegna nello stesso albero di rendering del
  sistema senza runtime aggiuntivo; Flutter porta con sé il proprio motore (binario più grande, memoria di
  base più alta, startup più pesante). Su device di fascia bassa e sul tempo di avvio, il nativo parte
  avvantaggiato.
- **Shader**: qui sono usati AGSL nativamente. In Flutter andrebbero riscritti come fragment `.frag` via
  `FragmentProgram` - fattibile, ma di nuovo un ponte, non accesso diretto.

Sintesi: per *spremere la piattaforma Android*, il nativo Compose è imbattibile; Flutter si avvicina molto
sull'hardware grafico, ma resta un passo indietro su integrazione di sistema e footprint.

---

## 4. Tabella vantaggi / svantaggi - app finale, **solo Android**

| Aspetto | Kotlin + Jetpack Compose (nativo) | Flutter (Dart) |
|---|---|---|
| **Dimensione APK/AAB** | ✅ Minima, nessun runtime extra | ❌ Più grande (engine + runtime Dart inclusi) |
| **Avvio a freddo / primo frame** | ✅ Più rapido | ⚠️ Più lento (inizializzazione engine) |
| **Footprint di memoria** | ✅ Inferiore | ⚠️ Superiore (base più alta) |
| **Accesso API Android** | ✅ Diretto, completo, immediato alle novità | ⚠️ Tramite plugin/channel; sottoinsieme, talvolta in ritardo |
| **Shader (AGSL)** | ✅ Nativi, integrazione diretta | ⚠️ Riscritti come fragment shader via ponte |
| **Haptics fini** | ✅ API completa (`VibrationEffect`) | ⚠️ Più limitati attraverso i plugin |
| **Fluidità gameplay 2D** | ✅ Ottima | ✅ Ottima (Impeller) - sostanzialmente alla pari |
| **Coerenza look Material / sistema** | ✅ Nativa (dynamic color, temi di sistema) | ⚠️ Material ricreato dal framework, non "di sistema" |
| **Integrazione Play (signing, App Bundle, Services)** | ✅ Percorso nativo, di prima classe | ✅ Supportato, ma con un layer in mezzo |
| **Maturità tooling (profiler, layout inspector)** | ✅ Android Studio nativo | ✅ Buono, ma alcuni strumenti restano lato nativo |
| **Velocità di iterazione UI** | ⚠️ Compose preview / live edit (buono) | ✅ Hot reload molto rapido |
| **Curva di apprendimento / ecosistema gioco** | ⚠️ Effetti/particelle da costruire a mano | ⚠️ Idem (nessuno dei due è un game engine) |
| **Riusabilità verso altre piattaforme** | ❌ Solo Android (salvo KMP) | ✅ Stesso codice anche iOS / web / desktop |

---

## 🧾 Conclusione

Limitatamente ad Android, **Kotlin + Compose vince su quasi tutti gli assi tecnici** (dimensione, avvio,
memoria, integrazione, shader, haptics) e **pareggia sulla fluidità grafica**. Flutter recupera solo su
hot-reload e - il punto decisivo nel contesto attuale - sulla **riusabilità cross-platform**, vantaggio che
però esiste *soltanto* nel momento in cui si aggiunge iOS.

Da qui il bivio: la scelta originale era ottima per l'obiettivo di ieri (Android-only); Flutter diventa
sensato per l'obiettivo di domani (Android + iOS). La decisione sul porting va quindi presa sul piano del
**prodotto** (voglio davvero iOS?), non su un presunto difetto tecnico della scelta Kotlin, che difetto non è.
