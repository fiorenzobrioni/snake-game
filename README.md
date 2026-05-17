# 🐍 Snake Game (.NET 10)

[![C#](https://img.shields.io/badge/language-C%23-239120?logo=c-sharp&logoColor=white)](https://dotnet.microsoft.com/)
[![.NET](https://img.shields.io/badge/.NET-10.0-512BD4?logo=dotnet&logoColor=white)](https://dotnet.microsoft.com/)
[![Platform](https://img.shields.io/badge/platform-Windows-0078D4)](https://www.microsoft.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Un classico ed intramontabile **Snake Game** sviluppato in **C#** e **Windows Forms**. 
Questo repository nasce come **progetto didattico** per esplorare le funzionalità di C# e testare la piattaforma **.NET 10**. È l'ideale per imparare i concetti base della programmazione a eventi, della gestione del rendering con GDI+ e per fare pratica con la gestione del game loop in un'applicazione desktop.

---

## 🎯 Panoramica del Progetto

Il progetto implementa le meccaniche classiche di Snake, estese con diverse funzionalità moderne e personalizzabili per rendere l'esperienza di gioco più dinamica:
- 🍎 **Sistema di Cibo Multiplo**: Diverse tipologie di cibo che garantiscono bonus differenti (lunghezza e punteggio). Il cibo raro dà più punti e fa crescere di più il serpente!
- 🚧 **Ostacoli Generati Casualmente**: La difficoltà aumenta introducendo blocchi grigi da evitare all'interno del campo.
- 🎚️ **Livelli di Difficoltà**: 5 livelli di sfida (da *Principiante* a *Leggenda*) che regolano la velocità di movimento del serpente e il numero di ostacoli presenti sul campo.
- 📐 **Dimensioni del Campo**: Possibilità di variare la grandezza dell'area di gioco in base a 5 template (da *Tascabile* 30x20 fino a *Infinito* 120x80).
- ⏸️ **Pausa e UI Dinamica**: Possibilità di mettere in pausa in qualsiasi momento e interfaccia responsive che si adatta al ridimensionamento della finestra.

---

## 🛠️ Requisiti

- **Sistema Operativo**: Windows (necessario per l'esecuzione di applicazioni Windows Forms)
- **SDK**: .NET 10.0 SDK o superiore installato sul sistema

---

## 🚀 Come Compilare e Avviare

1. **Clona il repository**:
   ```powershell
   git clone https://github.com/tuo-username/snake-game.git
   ```

2. **Entra nella cartella del progetto**:
   ```powershell
   cd src/SnakeGame
   ```

3. **Compila il progetto**:
   ```powershell
   dotnet build
   ```

4. **Avvia il gioco**:
   ```powershell
   dotnet run
   ```

---

## 🎮 Come Giocare

### Scopo del gioco
Guida il serpente all'interno del campo da gioco. Raccogli il cibo che appare casualmente per allungare il corpo del serpente e aumentare il tuo punteggio. 
Fai attenzione a non scontrarti contro i muri esterni, gli ostacoli posizionati nel campo o il tuo stesso corpo!

### Controlli
- ⬆️ ⬇️ ⬅️ ➡️ **Frecce Direzionali**: Muovono il serpente
- ␣ **Spazio** o **P**: Metti in Pausa o riprendi il gioco
- **R**: Ricomincia immediatamente la partita

---

## 👨‍💻 Struttura per lo Sviluppo (Per i curiosi)

Tutto il codice principale della logica, della UI e del rendering si trova centralizzato in:
`src/SnakeGame/SnakeForm.cs`

Il progetto illustra concetti base di architettura Windows Forms:
- **Game Loop**: Guidato da un componente `System.Windows.Forms.Timer` con intervallo dinamico.
- **Rendering 2D**: Effettuato in overlay e interamente basato su `Graphics` e `GDI+` tramite l'evento `Paint` di una `PictureBox`.
- **Gestione input**: Sovrascrittura di `ProcessCmdKey` per intercettare l'input della tastiera (frecce, comandi rapidi) a basso livello prima che vengano consumati dai controlli standard dell'interfaccia utente.

---

## 📄 Licenza

Questo progetto è distribuito sotto licenza **MIT**. Sentiti libero di clonarlo, studiarlo, modificarlo e usarlo per i tuoi esperimenti! Consulta il file [LICENSE](LICENSE) per ulteriori dettagli.
