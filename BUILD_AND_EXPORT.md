# GoNature — Exporting Runnable JARs (G9_client.jar / G9_server.jar)

This project ships as two runnable JARs that run on two machines on the same LAN:

- **G9_server.jar** — run on the machine that also hosts MySQL (`park_db`).
- **G9_client.jar** — run on any other machine; it asks for the server's IP at startup.

---

## 0. One-time prerequisites on the TARGET machines

Each machine that runs a JAR needs a **Java 21 (or newer) JRE/JDK** installed and on `PATH`
(`java -version` should print 21.x). The server machine additionally needs **MySQL** running
with the `park_db` schema loaded and the `root` account available.

JavaFX and the MySQL connector do **not** need to be installed separately — they are bundled
inside the JARs (see export steps below).

---

## 1. Why there are ServerLauncher / ClientLauncher classes

`ServerUI` and `ClientUI` both `extends javafx.application.Application`. If a JAR's
`Main-Class` is an `Application` subclass, `java -jar` fails with:

> Error: JavaFX runtime components are missing, and are required to run this application

The fix already added to the project: two tiny launcher classes that do **not** extend
`Application` and just call through to the real entry point:

- `Server.ServerLauncher`  → calls `ServerUI.main(...)`
- `client.ClientLauncher`   → calls `ClientUI.main(...)`

**Use these as the Main-Class when exporting** so the bundled JARs run with a plain
`java -jar`.

---

## 2. Export the SERVER JAR (G9_server.jar)

1. In Eclipse, run **`Server.ServerLauncher`** once as a Java Application so a launch
   configuration exists. (It will fail to find JavaFX unless you add the VM args in §4 —
   that's fine, you only need the launch config to exist. Alternatively just create the
   Run Configuration without running it.)
2. Right-click the **G9_Prototype_Server** project → **Export…**
3. Choose **Java → Runnable JAR file** → **Next**.
4. **Launch configuration:** select `ServerLauncher - G9_Prototype_Server`.
5. **Export destination:** `…\G9_server.jar`.
6. **Library handling:** choose **“Extract required libraries into generated JAR.”**
   This pulls JavaFX, the MySQL connector, and OCSF *inside* the JAR.
7. **Finish.** Accept any warnings about re-exported / duplicate `module-info` files
   (they are harmless — everything runs from the classpath).

The MySQL Connector/J and `db.properties` are both included automatically (the connector is
on the build path; `db.properties` lives in `src/` so it lands at the JAR root).

## 3. Export the CLIENT JAR (G9_client.jar)

Same procedure on the **G9_Prototype_Client** project:

1. Make a Run Configuration for **`client.ClientLauncher`**.
2. **Export… → Runnable JAR file.**
3. **Launch configuration:** `ClientLauncher - G9_Prototype_Client`.
4. **Export destination:** `…\G9_client.jar`.
5. **Library handling:** **“Extract required libraries into generated JAR.”**
6. **Finish.**

---

## 4. Running the JARs

With the launcher classes + “extract required libraries,” a plain command works:

```
java -jar G9_server.jar
java -jar G9_client.jar
```

JavaFX may print a one-line warning like *"Unsupported JavaFX configuration: classes were
loaded from 'unnamed module'"* — this is expected when JavaFX runs from the classpath and is
**not** an error; the app runs normally.

### Fallback (only if the bundled JAR ever refuses to start JavaFX)

Keep JavaFX external and pass it on the module path. The JavaFX SDK folder must exist on that
machine:

```
java --module-path "PATH\TO\javafx-sdk-21\lib" ^
     --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media ^
     -jar G9_server.jar
```

(Use the same form for the client.) These are also the VM arguments to put in the Eclipse Run
Configuration **VM arguments** box so the launchers run inside Eclipse:

```
--module-path "C:\Users\taise\OneDrive\Desktop\openjfx-21.0.5_windows-x64_bin-sdk\javafx-sdk-21.0.5\lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media
```

> Note: VM arguments are **not** baked into the exported JAR. They only matter when running
> inside Eclipse or when using the fallback launch command above.

---

## 5. db.properties — bundle it, no per-machine edit needed

- Only the **server** reads `db.properties`; it is loaded as a **classpath resource**
  (`getResourceAsStream("/db.properties")`), so it is bundled inside `G9_server.jar`.
- It points at `jdbc:mysql://localhost/park_db` — i.e. the MySQL instance on the **same
  machine as the server JAR**. The MySQL **password** is typed into the server's startup
  dialog and is never stored on disk.
- The **client never reads** `db.properties`. The only address the client needs is the
  **server's LAN IP**, which it asks for in a dialog at startup.

**Therefore nothing in `db.properties` changes when you move to another machine**, as long as
the server JAR and MySQL run on the same box. The IP that varies between setups is the
*server's* IP, supplied at runtime by the client.

**Only** if you intentionally put the MySQL database on a *third* machine (separate from the
server JAR) would you need to change `db.url`. Because the file is read from the classpath, the
cleanest way to do that is to edit `src/db.properties`, re-export, **or** launch the server with
the file on the classpath ahead of the JAR:

```
java -cp "db.properties_dir;G9_server.jar" Server.ServerLauncher
```

For the standard two-machine submission (server+DB together, client separate), just bundle it
and leave it alone.

---

## 6. Quick start for the grader

**Machine A (server + MySQL):**
```
mysql -u root -p park_db < GoNature_DB.sql      # load schema once
java -jar G9_server.jar                          # enter MySQL password, set port 5555, Start
```
Find this machine's LAN IP with `ipconfig` (e.g. 192.168.1.20).

**Machine B (client):**
```
java -jar G9_client.jar                          # enter Machine A's IP when prompted
```
