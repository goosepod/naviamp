#!/usr/bin/env python3
"""Inspect only the disposable packaged app through the real Linux AT-SPI bus."""
import argparse
import time
import gi
gi.require_version('Atspi', '2.0')
from gi.repository import Atspi, GLib

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('pid', type=int)
parser.add_argument('--action')
parser.add_argument('--focus')
parser.add_argument('--seek', type=float)
parser.add_argument('--focus-slider', action='store_true')
parser.add_argument('--watch-seconds', type=int, default=0)
args = parser.parse_args()
Atspi.init()
desktop = Atspi.get_desktop(0)
app = None
deadline = time.monotonic() + 10
while app is None and time.monotonic() < deadline:
    while GLib.MainContext.default().pending(): GLib.MainContext.default().iteration(False)
    apps = [desktop.get_child_at_index(i) for i in range(desktop.get_child_count())]
    app = next((a for a in apps if a.get_process_id() == args.pid), None)
    if app is None: time.sleep(.1)
if app is None:
    raise SystemExit('Disposable app is absent from AT-SPI; inspect the Java ATK bridge startup log')

def nodes(node, path='root', depth=0):
    if depth > 40:
        raise RuntimeError('Accessibility tree cycle')
    yield path, node
    for i in range(node.get_child_count()):
        child = node.get_child_at_index(i)
        if child is not None:
            yield from nodes(child, f'{path}/{i}', depth+1)

entries = list(nodes(app))
for path, node in entries:
    name = node.get_name()
    role = node.get_role_name()
    action = node.get_action_iface()
    actions = [action.get_action_name(i) for i in range(action.get_n_actions())] if action else []
    print(f'{path} role={role} name={name!r} actions={actions} focused={node.get_state_set().contains(Atspi.StateType.FOCUSED)}')
if args.focus_slider:
    target = next(node for _, node in entries if node.get_role() == Atspi.Role.SLIDER)
    assert target.get_component_iface().grab_focus(), 'Native progress focus failed'
    print('AT_SPI_PROGRESS_FOCUS requested')
if args.action or args.focus:
    target = next((node for _, node in entries if node.get_name() == (args.action or args.focus)), None)
    assert target is not None, 'Accessible target absent'
    if args.action:
        assert target.get_action_iface().do_action(0), 'Native accessibility action failed'
        print('AT_SPI_ACTION passed:', args.action)
    else:
        assert target.get_component_iface().grab_focus(), 'Native accessibility focus failed'
        print('AT_SPI_FOCUS requested:', args.focus)
if args.seek is not None:
    slider = next((node for _, node in entries if node.get_role() == Atspi.Role.SLIDER), None)
    assert slider is not None, 'Accessible progress slider absent'
    value = slider.get_value_iface()
    assert value.get_maximum_value() > value.get_minimum_value(), 'Native accessibility value range is empty'
    assert value.set_current_value(args.seek), 'Native accessibility seek failed'
    time.sleep(1)
    assert abs(value.get_current_value() - args.seek) < .02, 'Native seek returned success without changing playback'
    print('AT_SPI_SEEK passed:', args.seek)
if args.watch_seconds:
    def changed(event, *_):
        application = event.source.get_application()
        if application is not None and application.get_process_id() == args.pid:
            print(f'AT_SPI_EVENT type={event.type} name={event.source.get_name()!r}', flush=True)
    listener = Atspi.EventListener.new(changed, None)
    listener.register('object:property-change')
    listener.register('object:state-changed:focused')
    listener.register('object:text-changed')
    deadline = time.monotonic() + args.watch_seconds
    while time.monotonic() < deadline:
        while GLib.MainContext.default().pending(): GLib.MainContext.default().iteration(False)
        time.sleep(.05)
    listener.deregister('object:property-change')
    listener.deregister('object:state-changed:focused')
    listener.deregister('object:text-changed')
