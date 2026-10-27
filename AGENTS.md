# Istruzioni per gli agenti — IPTV Receiver

## Progetto e fonti

Client Android nativo per telefoni e tablet, scritto in Kotlin con Jetpack Compose
e Media3. L'utente collega il proprio provider Xtream; non sono inclusi abbonamenti,
cataloghi o un backend applicativo. Android TV è un obiettivo successivo.

- Usare `README.md` per l'avvio e `CONTRIBUTING.md` per le convenzioni.
- Consultare `docs/ARCHITECTURE.md` per i confini dei componenti,
  `docs/PROVIDER.md` per il protocollo, `SECURITY.md` per credenziali e controlli
  parentali, `docs/LOCALIZATION.md` per le traduzioni e `docs/VERIFICATION.md`
  per le evidenze e i limiti delle prove. Leggere le guide pertinenti alla modifica.
- Le versioni effettive sono nei file Gradle, in `gradle/libs.versions.toml`
  e in `gradle/wrapper/gradle-wrapper.properties`. In caso di descrizioni storiche
  discordanti, verificare questi file prima di proporre modifiche alle dipendenze.

## Configurazione e verifiche

Aprire la radice in Android Studio. Seguire i prerequisiti del README: JDK 17 o 21,
Android SDK Platform 37.2 e Build Tools 36.0.0. Configurare l'SDK tramite
`ANDROID_HOME` o un `local.properties` escluso da Git; non inserire percorsi della
macchina nei file condivisi. Usare il Wrapper incluso, senza Gradle globale.

Da PowerShell, per modifiche a modelli o provider:

```powershell
.\gradlew.bat verifyDependencyPolicy :core:model:test :core:provider:test
```

Prima della revisione di modifiche applicative, eseguire la verifica del README,
che corrisponde a `.github/workflows/android.yml`:

```powershell
.\gradlew.bat --no-daemon verifyDependencyPolicy :core:model:test :core:provider:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

Per pagine pubbliche, metadati e collegamenti del sito:

```powershell
python tools/check_site.py
```

Il controllo del sito usa Python 3.10 o successivo e la libreria standard.
Su Linux e macOS usare `./gradlew` per gli stessi task. Se mancano strumenti o
dispositivi, riportare il controllo non eseguito e la causa. Test JVM, lint e build
non dimostrano la compatibilità della riproduzione: per modifiche al player
documentare anche dispositivi fisici, formati e interazioni effettivamente provati.

## Confini del codice

- Conservare la direzione `app -> core:provider -> core:model`.
  `core:model` resta indipendente da Android e dalla rete; i moduli core non
  devono dipendere dall'app. HTTP e JSON appartengono al provider dietro
  `CatalogRepository`, lo stato ai ViewModel e la composizione alle schermate.
- Propagare la cancellazione delle coroutine fino alle richieste; non assorbirla
  con gestori generici delle eccezioni.
- Usare fixture sintetiche e falsi repository per parsing, URL, errori e stato.
  I controlli automatici non devono richiedere un account reale.
- Conservare la separazione della cronologia per account e la compatibilità
  dello schema persistito; verificare le migrazioni quando cambia il formato.
- Seguire `.editorconfig`. Dichiarare dipendenze fissate e stabili nel catalogo
  delle versioni, secondo `docs/DEPENDENCY_POLICY.md`; mantenere insieme Wrapper
  e checksum. Evitare riformattazioni e riorganizzazioni estranee alla richiesta.

## Dati, contenuti e pubblicazione

Credenziali, URL di riproduzione e risposte del provider non devono entrare nei
log, nelle rotte di navigazione, negli esempi o nelle acquisizioni pubbliche.
Non serializzare password o URL completi nella cronologia. Conservare la verifica
TLS, l'archiviazione opzionale con Android Keystore e i controlli parentali con
blocco predefinito dei contenuti per adulti; errori di lettura non devono sbloccarli.

La cartella `store/` contiene materiale preparatorio: seguirne il README solo per
attività di pubblicazione o acquisizione di immagini. Usare il catalogo fittizio;
tenere chiavi di firma e password fuori da Git. Una build locale non equivale a
una pubblicazione sullo store. L'identità Git e l'autenticazione restano nelle
istruzioni globali e negli strumenti locali; non copiarle qui.

## Regole di revisione

- Segnalare accessi di rete introdotti nei Composable o dipendenze Android nei
  moduli core; mantenere il confine `CatalogRepository`.
- Segnalare percorsi di catalogo, ricerca, cronologia o playback che aggirano i
  controlli parentali; applicare la stessa politica anche dopo errori e ripristini.
- Segnalare persistenza o diagnostica che espone dati del provider; conservare
  soltanto gli identificatori necessari e verificare l'isolamento degli account.

## Manutenzione delle istruzioni

Aggiornare questa guida quando cambiano comandi o vincoli del repository.
Usare file annidati solo per regole specifiche di una sottocartella.
Riferimento: [documentazione ufficiale OpenAI su AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
