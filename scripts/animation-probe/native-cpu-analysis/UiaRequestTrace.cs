using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using Microsoft.Diagnostics.Tracing;
using Microsoft.Diagnostics.Tracing.Session;

// Test diagnostics only. No UI input, accessibility setting changes or kernel profiling.
static class UiaRequestTrace
{
    static readonly Guid Provider = new Guid("820a42d8-38c4-465d-b64e-d7d56ea1d612");

    public static void Run(string[] args)
    {
        if (args.Length < 3)
            throw new ArgumentException("Usage: --uia-capture|--uia-summary trace.etl targetPid [seconds]");
        int pid = int.Parse(args[2], CultureInfo.InvariantCulture);
        if (pid <= 0) throw new ArgumentException("Invalid target PID");
        if (args[0] == "--uia-capture") Capture(args, pid);
        else if (args[0] == "--uia-summary") Summarize(args[1], pid);
        else throw new ArgumentException("Unknown UI Automation diagnostic mode");
    }

    static void Capture(string[] args, int pid)
    {
        int seconds = args.Length > 3 ? int.Parse(args[3], CultureInfo.InvariantCulture) : 10;
        if (seconds < 5 || seconds > 30) throw new ArgumentException("Capture must be 5–30 seconds");
        string trace = Path.GetFullPath(args[1]);
        string build = Path.GetFullPath("build") + Path.DirectorySeparatorChar;
        if (!trace.StartsWith(build, StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("Run from the checkout root; output must be under its ignored build directory");
        if (File.Exists(trace) || File.Exists(trace + ".target.json"))
            throw new IOException("Refusing to overwrite an existing capture");
        using (var target = Process.GetProcessById(pid))
        {
            // Retain the process identity throughout capture. The app itself stays unelevated.
            var handle = target.Handle;
            string executable = target.MainModule.FileName;
            string desktopBuild = Path.GetFullPath("apps/desktop/build") + Path.DirectorySeparatorChar;
            if (target.ProcessName != "Naviamp" ||
                !(executable.StartsWith(build, StringComparison.OrdinalIgnoreCase) ||
                  executable.StartsWith(desktopBuild, StringComparison.OrdinalIgnoreCase)))
                throw new ArgumentException("Target must be a disposable Naviamp build in this checkout");
            Directory.CreateDirectory(Path.GetDirectoryName(trace));
            string instance = "NaviampUIA-" + Guid.NewGuid().ToString("N");
            File.WriteAllText(trace + ".target.json", JsonSerializer.Serialize(new {
                ApplicationProcessId = pid, ApplicationStartTime = target.StartTime.ToUniversalTime(),
                Executable = executable, RequestedSeconds = seconds, Instance = instance
            }));
            using (var session = new TraceEventSession(instance, trace))
            {
                // Start/stop request execution plus peer-PID receive/response correlation only.
                // This avoids the high-volume global provider/MSAA callout events.
                var options = new TraceEventProviderOptions {
                    ProcessIDFilter = new List<int> { pid },
                    EventIDsToEnable = new List<int> { 523, 524, 545, 546 }
                };
                session.EnableProvider(Provider, TraceEventLevel.Verbose, 0x10000, options);
                Thread.Sleep(seconds * 1000);
            } // Dispose stops only this unique session, including on exceptions.
            if (target.HasExited) throw new InvalidOperationException("Target exited; retain capture as rejected");
        }
    }

    static void Summarize(string path, int pid)
    {
        var peers = new Dictionary<int, int>();
        var methods = new Dictionary<string, int>();
        int targetEvents = 0;
        using (var source = new ETWTraceEventSource(path))
        {
            source.Dynamic.All += e => {
                if (e.ProviderGuid != Provider || e.ProcessID != pid) return;
                targetEvents++;
                if ((int)e.ID == 545)
                    Add(peers, Convert.ToInt32(e.PayloadByName("PeerProcessId"), CultureInfo.InvariantCulture));
                if ((int)e.ID == 523)
                    Add(methods, Convert.ToString(e.PayloadByName("MethodName"), CultureInfo.InvariantCulture));
            };
            source.Process();
            var identities = peers.OrderByDescending(x => x.Value).Select(x => {
                string name = "unverified/exited";
                try {
                    using (var process = Process.GetProcessById(x.Key))
                        if (process.StartTime.ToUniversalTime() <= source.SessionStartTime.ToUniversalTime())
                            name = process.ProcessName;
                } catch (ArgumentException) { }
                  catch (InvalidOperationException) { }
                  catch (System.ComponentModel.Win32Exception) { }
                return new { ProcessId = x.Key, ProcessName = name, IncomingRequestEvents = x.Value };
            }).ToArray();
            // Deliberately omit arbitrary payloads: no UI text, property values or unrelated processes.
            Console.WriteLine(JsonSerializer.Serialize(new {
                TargetPid = pid, EventsLost = source.EventsLost, TargetEvents = targetEvents,
                IncomingRequestEvents = peers.Values.Sum(), Requesters = identities, Methods = methods
            }, new JsonSerializerOptions { WriteIndented = true }));
        }
    }

    static void Add<T>(Dictionary<T, int> counts, T key)
    {
        int count;
        counts.TryGetValue(key, out count);
        counts[key] = count + 1;
    }
}
