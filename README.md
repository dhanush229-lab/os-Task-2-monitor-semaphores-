# ConcurLab — Operating Systems Synchronization Studio

An original Java 21 + HTML/CSS/JavaScript project covering four synchronization variants.

## Run on Windows

1. Install JDK 17 or newer and check `java -version` and `javac -version` in Command Prompt.
2. Extract this ZIP to a normal folder. Double-click `run.bat` (or open Command Prompt in that folder and run `run.bat`).
3. Keep the terminal open and visit **http://localhost:8765** in Chrome or Edge.
4. Pick one of the four modes. Click **START EXECUTION**. Java creates actual threads on each click and streams JSONL events over the HTTP response. The page replays the actual Java events at a separate, adjustable learning speed (Java threads are not slowed). Use Pause, Resume, Step, and Reset to inspect resource changes. Use **EXPORT LOG** to download a CSV for that run.
5. Close the terminal / press Ctrl+C to stop the backend.

## Run on Linux/macOS

```bash
./run.sh
# then open http://localhost:8765
```

## Four implementations

1. **Philosophers / Semaphore:** four threads and four fair binary semaphores in an array. Even philosophers take left then right, odd philosophers take right then left; this breaks circular wait. Starvation is not formally excluded by this algorithm.
2. **Philosophers / Monitor:** one synchronized monitor allocates both chopsticks atomically, using `wait()` and `notifyAll()` when a pair is unavailable. This avoids hold-and-wait deadlock; fairness is not promised.
3. **Readers–Writers / Semaphore:** fair turnstile semaphore and room-empty semaphore plus a reader-count lock, enabling multiple concurrent readers and exclusive writers. Fair Java Semaphore gives FIFO-ish contention ordering; scheduling does not imply hard real-time bounds.
4. **Readers–Writers / Monitor:** synchronized monitor with `ArrayDeque<String>` request queue and conditional `wait()`/`notifyAll()`. Readers at the head of the queue can be admitted together, but queued writers block later readers.

## Files

- `src/SynchronizationServer.java`: Java HTTP server, actual thread implementations, log streaming
- `web/index.html`, `web/style.css`, `web/script.js`: browser UI, animations, display of real Java events
- `results/*.jsonl`: recorded Java execution logs from the development test run
- `results/*.png`: UI screenshots obtained by rendering the interface with replay of actual captured logs (browser sandbox could not access localhost during screenshot capture); they are not screenshots of a live localhost-connected browser
- `verify.py`: checks ordering, end conditions, resource conflicts on logged run
- `run.bat`, `run.sh`: launchers
- `OS_Assignment_Report.docx` and `.pdf`: original report, with evidence and references

## Important technical notes

- The server listens on `127.0.0.1:8765` only; no third-party Java dependencies are required.
- Backend traces are serialized with a synchronized writer so the network stream stays valid.
- Only one simulation is admitted at a time (HTTP 409 for concurrent start attempts).
- The code simulates a finite demonstration workload, not an endless production service.
- The 4-mode implementation uses one Java source file to keep building simple; each algorithm is a separate function/class.
- The dashboard requires the Java server; opening `index.html` directly as a file does not execute Java.
- The run durations are intentionally affected by `Thread.sleep()` so animations are visible; they are **not** CPU-throughput benchmarks or evidence that one algorithm is generally fastest.
- Run `python verify.py` for the included recorded logs, or capture new streams with POST to `/api/run?mode=rw-monitor` etc.

## Updated visual playback

Start executes the Java backend; the browser buffers authentic NDJSON events and presents them at the selected playback pace. The **Elapsed Time** metric is Java execution time, not animation time. Pause and Step change only what is displayed, not the running Java threads. This is a teaching visualization rather than a concurrency debugger or throughput benchmark.

## Control-center visual design (final selection)

The frontend uses Design B with **A1** moderate motion, **B1** formal technical terminology, **C1** contextual explanations, and **no audio**. The default guided mode presents one real Java event every 900 ms; Normal (550 ms) and Fast (200 ms) are optional. A slow radar sweep, subtle blue active-state glow, event highlighting, and keyboard-visible focus provide restrained technical effects. Pause/Step/Reset affect presentation; the Java algorithm itself continues independently. Actual run timing and visual playback duration are separate metrics.
