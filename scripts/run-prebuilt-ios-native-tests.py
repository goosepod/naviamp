#!/usr/bin/env python3
"""Execute the compiled Kotlin/Native suite in an owned booted simulator, without Gradle startup."""
import argparse
import datetime
import hashlib
import html
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


TLS_OWNER = 'app.naviamp.ios.playback.IosMutualTlsPlaybackIntegrationTest'
TLS_CASE = 'realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback'


def inventory(text):
    cases = set()
    owner = None
    for line in text.splitlines():
        if not line.strip():
            continue
        if line.startswith('  ') and owner:
            case = (owner, line.strip())
            if case in cases:
                raise RuntimeError('Duplicate native inventory entry')
            cases.add(case)
        elif not line[0].isspace() and line.endswith('.'):
            owner = line[:-1]
        else:
            raise RuntimeError(f'Invalid native inventory line: {line!r}')
    if not cases or (TLS_OWNER, TLS_CASE) not in cases:
        raise RuntimeError('Native inventory is empty or lacks the required mTLS contract')
    return cases


def service_message(line):
    """Parse Kotlin 2.3's TeamCity logger; reject malformed/unknown escaping, never guess."""
    if not line.startswith('##teamcity[') or not line.endswith(']'):
        raise RuntimeError('Malformed native service message')
    body = line[11:-1]
    match = re.match(r'(\w+)(?: |$)', body)
    if not match:
        raise RuntimeError('Missing native event name')
    event = match[1]
    pos = len(event)
    fields = {}
    escapes = {'|': '|', "'": "'", 'n': '\n', 'r': '\r', '[': '[', ']': ']'}
    while pos < len(body):
        match = re.match(r" ([A-Za-z]+)='", body[pos:])
        if not match or match[1] in fields:
            raise RuntimeError('Malformed or duplicate native event field')
        key = match[1]
        pos += match.end()
        value = []
        while pos < len(body) and body[pos] != "'":
            char = body[pos]
            pos += 1
            if char == '|':
                if pos >= len(body) or body[pos] not in escapes:
                    raise RuntimeError('Invalid native event escape')
                char = escapes[body[pos]]
                pos += 1
            value.append(char)
        if pos == len(body):
            raise RuntimeError('Unterminated native event field')
        pos += 1
        fields[key] = ''.join(value)
    return event, fields


def results(text, expected):
    owner = None
    active = None
    completed = {}
    for line in text.splitlines():
        if '##teamcity[' not in line:
            continue
        # Native diagnostic output may precede a service message on the same line.
        event, fields = service_message(line[line.index('##teamcity['):])
        name = fields.get('name')
        if event == 'testSuiteStarted' and owner is None and active is None and name:
            owner = name
        elif event == 'testSuiteFinished' and name == owner and active is None:
            owner = None
        elif event == 'testStarted' and owner and active is None:
            active = (owner, name)
            if active not in expected or active in completed:
                raise RuntimeError(f'Unexpected or repeated native case: {active}')
        elif event == 'testFinished' and active == (owner, name):
            duration = fields.get('duration', '')
            if not duration.isdecimal():
                raise RuntimeError('Missing or invalid native duration')
            completed[active] = int(duration) / 1000
            active = None
        elif event in ('testFailed', 'testIgnored'):
            raise RuntimeError(f'Native case failed or was ignored: {owner}.{name}: {fields}')
        else:
            raise RuntimeError(f'Unexpected native event/order: {event}: {fields}')
    if owner is not None or active is not None or set(completed) != expected:
        raise RuntimeError('Native results are incomplete or do not match the binary inventory')
    if 'mTLS runner bootstrap supplied' not in text:
        raise RuntimeError('Native mTLS bootstrap provenance was not reported')
    return completed


def execute(command, log, deadline):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError('Prebuilt native execution budget exhausted')
    with log.open('w') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=remaining)
        except BaseException:
            # Only the process group created by this Popen is targeted. Simulator/fixture
            # ownership and shutdown remain with the caller's existing cleanup guards.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
            raise
    text = log.read_text(errors='replace')
    print(text, end='', flush=True)
    if code != 0:
        raise RuntimeError(f'Native command exited {code}; see {log.name}')
    return text


def run(executable, device, output, timeout=240, prefix=None, required=None):
    uuid.UUID(device)
    executable = executable.resolve(strict=True)
    if not executable.is_file():
        raise RuntimeError('Native executable is not a file')
    # Never let a previous execution's results satisfy this invocation.
    output.mkdir(parents=True, exist_ok=False, mode=0o700)
    started = time.monotonic()
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    digest = hashlib.sha256(executable.read_bytes()).hexdigest()
    report = dict(status='failed', executable=str(executable), sha256=digest, device=device,
                  startedAt=stamp, budgetSeconds=timeout)
    # Match KotlinNativeSimulatorTest's argument boundary: native flags follow a literal
    # separator so CoreSimulator does not interpret them as its own spawn options.
    command = (prefix if prefix is not None else ['xcrun', 'simctl', 'spawn', device]) + [str(executable), '--']
    try:
        expected = inventory(execute(command + ['--ktest_list_tests'], output / 'inventory.log', started + timeout))
        if required is None:
            required = {tuple(case) for case in json.loads(Path(__file__).with_name('ios-native-required-tests.json').read_text())}
        if expected != required:
            raise RuntimeError(f'Native binary inventory differs from the required source manifest: missing={required - expected}; extra={expected - required}')
        report['expectedCases'] = sorted(expected)
        captured = execute(command + ['--ktest_logger=TEAMCITY'], output / 'native.log', started + timeout)
        completed = results(captured, expected)
        if hashlib.sha256(executable.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Native executable changed during execution')
        target = output / 'results'
        target.mkdir()
        for owner in sorted({owner for owner, _ in completed}):
            suite_cases = [(name, duration) for (suite, name), duration in completed.items() if suite == owner]
            suite = ET.Element('testsuite', name=owner, tests=str(len(suite_cases)), failures='0', errors='0',
                               skipped='0', timestamp=stamp, time=str(sum(d for _, d in suite_cases)))
            for name, duration in suite_cases:
                ET.SubElement(suite, 'testcase', classname=owner, name=name, time=str(duration))
            ET.SubElement(suite, 'system-out').text = captured
            # File names are generated from the Kotlin suite name, never arbitrary output paths.
            if not re.fullmatch(r'[A-Za-z0-9_.$]+', owner):
                raise RuntimeError('Unsafe native suite name')
            ET.ElementTree(suite).write(target / f'TEST-{owner}.xml', encoding='utf-8', xml_declaration=True)
        report.update(status='passed', tests=len(completed), failures=0, errors=0, skipped=0,
                      executedCases=sorted(completed))
        (output / 'report').mkdir()
        counters = ''.join(f'<div id="{key}"><div class="counter">{value}</div></div>'
                           for key, value in [('tests', len(completed)), ('failures', 0), ('ignored', 0)])
        (output / 'report/index.html').write_text(
            '<!doctype html><meta charset="utf-8"><title>Prebuilt iOS native tests</title>'
            '<h1>Prebuilt iOS native tests</h1>' + counters + '<p>Fresh execution: ' + html.escape(stamp) +
            '</p><pre>' + html.escape(captured) + '</pre>', encoding='utf-8')
        print(f'Naviamp prebuilt native execution completed: {len(completed)} tests, zero failures/errors/skips', flush=True)
        return completed
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        report['seconds'] = time.monotonic() - started
        (output / 'execution.json').write_text(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--executable', type=Path, required=True)
    parser.add_argument('--device', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    def interrupted(signum, frame):
        raise InterruptedError('Prebuilt native execution interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    run(args.executable, args.device, args.output)


if __name__ == '__main__':
    main()
