"""TV launch/navigation evidence. Records live playback attempt without overstating it."""
import glob
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = Path('smoke-results')
OUT.mkdir(exist_ok=True)
APP = 'com.tihulu.tube'
ACTIVITY = APP + '/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity'

def adb(*args, binary=False, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True, text=not binary).stdout

def capture(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml', check=False)
    xml = adb('shell', 'cat', '/sdcard/window.xml', check=False)
    (OUT / (name + '.xml')).write_text(xml)
    try:
        return [{k:n.attrib.get(k) for k in ['text','content-desc','resource-id','bounds']}
                for n in ET.fromstring(xml).iter('node') if n.attrib.get('focused') == 'true']
    except ET.ParseError:
        return []

apks = glob.glob('emulator-apk/*.apk')
assert len(apks) == 1, apks
print(adb('install', '-r', apks[0]))
adb('logcat', '-c')
adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
time.sleep(25)
start_pid = adb('shell', 'pidof', APP).strip()
assert start_pid, 'App did not survive launch'
before = capture('01-home')
adb('shell', 'input', 'keyevent', '21')
time.sleep(1)
adb('shell', 'input', 'keyevent', '20')
time.sleep(3)
after = capture('02-dpad-navigation')
(OUT/'memory-home.txt').write_text(adb('shell','dumpsys','meminfo',APP))

# Exercise public deep-link routing and preserve evidence; network access can fail on CI IPs.
adb('shell','am','start','-W','-n',ACTIVITY,'-a','android.intent.action.VIEW',
    '-d','https://www.youtube.com/watch?v=aqz-KE-bpKQ')
time.sleep(35)
capture('03-playback-attempt')
adb('shell','input','keyevent','23')
time.sleep(3)
capture('04-player-controls')
(OUT/'memory-player.txt').write_text(adb('shell','dumpsys','meminfo',APP))
adb('shell','input','keyevent','4')
time.sleep(3)
capture('05-back')
end_pid = adb('shell','pidof',APP).strip()
logs = adb('logcat','-d')
(OUT/'logcat.txt').write_text(logs)
crashed = 'FATAL EXCEPTION' in logs and ('Process: ' + APP) in logs
result = {'build_run':os.environ.get('APK_BUILD_RUN'), 'start_pid':start_pid, 'end_pid':end_pid,
          'focus_before':before, 'focus_after':after, 'focus_changed':before != after,
          'survived':bool(end_pid) and not crashed,
          'playback':'Attempt recorded; inspect screenshots/logs. Not an ad-blocking certification.',
          'device':'Android TV API 29 x86, 2048 MB RAM. Does not validate ARM hardware codecs.'}
(OUT/'result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
assert end_pid and not crashed, 'App crashed; inspect smoke-results'
