"""Serialize local tvOS builds and clean up only their owned native resources."""
from contextlib import contextmanager
from pathlib import Path


@contextmanager
def cancellation_signals():
    """Let CLI termination unwind owned-resource cleanup just like keyboard cancellation."""
    import signal
    previous = signal.getsignal(signal.SIGTERM)

    def terminate(signum, frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        yield
    finally:
        signal.signal(signal.SIGTERM, previous)


@contextmanager
def exclusive_build(root):
    import fcntl
    path = Path(root)/'build/tvos-heavy-build.lock'
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a') as lock:
        print('Waiting for the local tvOS build lease', flush=True)
        fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            print('Acquired the local tvOS build lease', flush=True)
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def terminate_tree(pids):
    """Terminate only captured process trees, including single-use Gradle daemon children."""
    import os
    import signal
    import subprocess
    import time
    rows = {}
    for line in subprocess.check_output(['ps', '-axo', 'pid=,ppid=,command='], text=True).splitlines():
        parts = line.strip().split(None, 2)
        if len(parts) == 3: rows[int(parts[0])] = (int(parts[1]), parts[2])
    owned = set(pids) & rows.keys()
    while True:
        added = {pid for pid, (parent, _) in rows.items() if parent in owned} - owned
        if not added: break
        owned |= added
    for sig in (signal.SIGTERM, signal.SIGKILL):
        for pid in sorted(owned, reverse=True):
            try:
                # Verify identity again before signaling; don't act on a recycled PID.
                current = subprocess.check_output(['ps', '-p', str(pid), '-o', 'command='], text=True).strip()
                if current == rows[pid][1]: os.kill(pid, sig)
            except (ProcessLookupError, subprocess.CalledProcessError): pass
        if sig == signal.SIGTERM and owned: time.sleep(2)


def terminate_consumer_gradle(root):
    """A generated snapshot is tool-owned; never stop a shared/user Gradle daemon."""
    import subprocess
    if not (Path(root)/'consumer-inputs.json').is_file(): return
    wrapper = str(Path(root)/'gradle/wrapper/gradle-wrapper.jar')
    pids = []
    for line in subprocess.check_output(['ps', '-axo', 'pid=,command='], text=True).splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and ('-jar '+wrapper+' ') in parts[1] and '-Dorg.gradle.daemon=false' in parts[1]:
            pids.append(int(parts[0]))
    terminate_tree(pids)


def run_owned_command(command, *, cwd, env, stdout, timeout):
    """Cancellation and timeouts must stop Xcode and its single-use compiler tree."""
    import subprocess
    process = subprocess.Popen(command, cwd=cwd, env=env, stdout=stdout, stderr=subprocess.STDOUT)
    try:
        return process.wait(timeout=timeout)
    except BaseException as failure:
        # Capture descendants before killing Xcode, which can orphan its Gradle wrapper.
        for cleanup in (lambda: terminate_tree([process.pid]),
                        lambda: terminate_consumer_gradle(cwd),
                        lambda: process.kill() if process.poll() is None else None,
                        lambda: process.wait(timeout=10)):
            try:
                cleanup()
            except Exception as error:
                failure.add_note('Owned build cleanup: ' + str(error))
        raise


def cleanup_owned_scene(device, fixture):
    """Attempt every cleanup even when another effect fails; never select other devices."""
    import json
    import subprocess
    result, errors = {}, []
    if fixture is not None:
        result['fixture'] = 0
        try:
            fixture.terminate()
            try:
                fixture.wait(timeout=10)
            except subprocess.TimeoutExpired:
                fixture.kill()
                fixture.wait(timeout=10)
        except Exception as error:
            result['fixture'] = 1
            errors.append('fixture: ' + str(error))
    if device is not None:
        state = 'Unknown'
        try:
            devices = json.loads(subprocess.check_output(
                ['xcrun', 'simctl', 'list', 'devices', '--json'], text=True, timeout=15))
            state = next((d['state'] for rows in devices['devices'].values()
                          for d in rows if d['udid'] == device), None)
        except Exception as error:
            errors.append('device inspection: ' + str(error))
        for action in ('shutdown', 'delete'):
            result[action] = 0
            if state is None or (action == 'shutdown' and state == 'Shutdown'):
                continue
            try:
                completed = subprocess.run(['xcrun', 'simctl', action, device],
                                           capture_output=True, timeout=30)
                result[action] = completed.returncode
                if completed.returncode:
                    errors.append(action + ': ' + completed.stderr.decode(errors='replace'))
            except Exception as error:
                result[action] = 1
                errors.append(action + ': ' + str(error))
    return result, errors
