# Radio completion callback ordering (#333)

TV acceptance of candidate `a3776547` stopped at the end of an eleven-track Sonic queue with
Autoplay enabled and batch ten. An independent diagnostic returned twenty matches. That diagnostic
does not establish unique eligible continuation candidates or prove why the earlier request stopped.
Windows comparisons with other seeds and Autoplay twenty succeeded for Instant Mix and Sonic.

Investigation found a concrete shared adapter defect: after accepting Finished for one engine
execution, it still accepted late Playing/Loading, duplicate Finished, and teardown progress.
The shared playback controller cancels pending Autoplay when state leaves Finished. The new
synthetic engine regression reproduced `[Playing, Finished, Playing, Loading, Finished]` before
the fix. Seventy-two focused adapter/controller tests ran; only the new regression failed.

The adapter now latches completion per engine request. The latch uses a shared atomic boolean so
native end-sync and polling observe completion and duplicate completion competes atomically. Late
callbacks from that completed request are ignored. Starting a new play creates a new latch; explicit
Stop still invalidates the request and publishes Stopped. Provider/source, queue and settings guards
on pending Autoplay remain unchanged, as do candidate ranking, exclusions, timeouts and batch sizes.

The adapter/controller suite passes after the correction. Verification commands/results are recorded
with the pull request. No production platform files, settings, strings or migrations changed.

This corrects the reproduced callback defect; it does not establish the original TV failure's root
cause. Physical TV retest remains pending because the owner paused device use. No TV commands,
installation or server playlist mutations are part of this fix. Proactive Radio top-up is not
implemented by the current queue-end Autoplay design and is not asserted as passing here.
