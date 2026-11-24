#!/usr/bin/env python3
"""Make the CloudGaming home screen the launcher activity of the Moonlight build.

Usage: patch_manifest.py path/to/AndroidManifest.xml
PcView (the stock host list) stays in the app and opens from the home screen.
"""
import re
import sys

path = sys.argv[1]
src = open(path, encoding="utf-8").read()

if "cloudgaming.CloudGamingActivity" in src:
    print("already patched")
    sys.exit(0)

# Drop the phone launcher category from PcView only (TV launcher stays).
m = re.search(r'<activity\s+android:name="\.PcView".*?</activity>', src, re.S)
if not m:
    sys.exit("PcView activity not found")
pc = m.group(0)
pc_new = re.sub(r'\s*<category android:name="android\.intent\.category\.LAUNCHER"\s*/>', "", pc, count=1)
pc_new = re.sub(r'\s*<category android:name="android\.intent\.category\.MULTIWINDOW_LAUNCHER"\s*/>', "", pc_new, count=1)
src = src.replace(pc, pc_new)

activity = '''
        <activity
            android:name=".cloudgaming.CloudGamingActivity"
            android:exported="true"
            android:label="@string/app_label"
            android:resizeableActivity="true"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.MULTIWINDOW_LAUNCHER" />
            </intent-filter>
        </activity>
'''
src = src.replace(pc_new, pc_new + activity, 1)
open(path, "w", encoding="utf-8").write(src)
print("patched", path)
