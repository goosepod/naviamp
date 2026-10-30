using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using Microsoft.Diagnostics.Symbols;
using Microsoft.Diagnostics.Tracing;
using Microsoft.Diagnostics.Tracing.Etlx;
using Microsoft.Diagnostics.Tracing.Parsers.Kernel;

// Diagnostic only: ETW itself contributes CPU. This is not an acceptance benchmark.
class Program
{
    static readonly string[] Groups = {
        "uiautomationcore!", "ntoskrnl!Etwp", "ntoskrnl!RtlWalkFrameChain",
        "awt!", "jvm!", "bass!", "skiko-windows-x64!", "dcomp!"
    };

    static void Main(string[] args)
    {
        if (args.Length < 4)
            throw new ArgumentException("Usage: trace.etl targetPid startSeconds endSeconds [symbolCache]");
        int targetPid = int.Parse(args[1], CultureInfo.InvariantCulture);
        double start = double.Parse(args[2], CultureInfo.InvariantCulture) * 1000;
        double end = double.Parse(args[3], CultureInfo.InvariantCulture) * 1000;
        if (targetPid <= 0 || !double.IsFinite(start) || !double.IsFinite(end) || start < 0 || end <= start)
            throw new ArgumentException("Invalid target/window");

        using (var log = TraceLog.OpenOrConvert(args[0]))
        {
            // Resolve only matching Windows system modules. JVM JIT frames remain unresolved.
            if (args.Length > 4)
            {
                using (var reader = new SymbolReader(Console.Error,
                    "srv*" + Path.GetFullPath(args[4]) + "*https://msdl.microsoft.com/download/symbols"))
                {
                    var names = new[] { "ntoskrnl", "ntdll", "kernelbase", "uiautomationcore",
                        "user32", "win32u", "win32kfull" };
                    foreach (var module in log.ModuleFiles)
                        if (names.Any(n => Path.GetFileNameWithoutExtension(module.FilePath)
                            .Equals(n, StringComparison.OrdinalIgnoreCase)))
                            log.CodeAddresses.LookupSymbolsForModule(reader, module);
                }
            }

            Console.WriteLine("SampleInterval=" + log.SampleProfileInterval);
            Console.WriteLine("DurationMs=" + log.SessionDuration.TotalMilliseconds + " EventsLost=" + log.EventsLost);
            var groups = new Dictionary<string, int>();
            var threadGroups = new Dictionary<string, int>();
            var threads = new Dictionary<int, int>();
            var modules = new Dictionary<string, int>();
            var stacks = new Dictionary<string, int>();
            var bins = new Dictionary<int, int>();
            int count = 0, noStack = 0;
            foreach (var e in log.Events)
            {
                var sample = e as SampledProfileTraceData;
                if (sample == null || sample.ProcessID != targetPid ||
                    sample.TimeStampRelativeMSec < start || sample.TimeStampRelativeMSec >= end)
                    continue;
                count++;
                Add(threads, sample.ThreadID);
                Add(bins, (int)(sample.TimeStampRelativeMSec / 1000));
                var stack = sample.CallStack();
                if (stack == null) { noStack++; continue; }
                var frames = new List<string>();
                for (var frame = stack; frame != null; frame = frame.Caller)
                {
                    var address = frame.CodeAddress;
                    frames.Add(address.ModuleName + "!" + address.FullMethodName + "@" + address.Address.ToString("x"));
                }
                var joined = string.Join(";", frames);
                // Inclusive groups overlap; count each group at most once per sample.
                foreach (var group in Groups)
                    if (joined.Contains(group))
                    {
                        Add(groups, group);
                        Add(threadGroups, sample.ThreadID + " " + group);
                    }
                Add(modules, stack.CodeAddress.ModuleName ?? "unknown");
                Add(stacks, joined);
            }
            Console.WriteLine("TargetPid=" + targetPid + " WindowMs=" + start + ".." + end +
                " TargetSamples=" + count + " MissingStacks=" + noStack);
            Print("INCLUSIVE", groups);
            Print("THREADGROUP", threadGroups);
            Print("THREAD", threads);
            foreach (var bin in bins.OrderBy(x => x.Key))
                Console.WriteLine("SECOND " + bin.Key + " " + bin.Value);
            Print("MODULE", modules);
            foreach (var stack in stacks.OrderByDescending(x => x.Value).Take(80))
                Console.WriteLine("STACK " + stack.Value + " " + stack.Key);
            if (count == 0) throw new InvalidOperationException("No target CPU samples in selected window");
        }
    }

    static void Add<T>(Dictionary<T, int> counts, T key)
    {
        int count;
        counts.TryGetValue(key, out count);
        counts[key] = count + 1;
    }

    static void Print<T>(string label, Dictionary<T, int> counts)
    {
        foreach (var entry in counts.OrderByDescending(x => x.Value))
            Console.WriteLine(label + " " + entry.Key + " " + entry.Value);
    }
}
