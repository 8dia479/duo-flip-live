"""Structural regression checks; these do not replace Android UI tests."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class LauncherTest(unittest.TestCase):
    def test_launcher_is_flip_home_not_wizard(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        launchers = []
        for activity in manifest.findall('./application/activity'):
            for intent in activity.findall('intent-filter'):
                actions = {x.get(ANDROID+'name') for x in intent.findall('action')}
                cats = {x.get(ANDROID+'name') for x in intent.findall('category')}
                if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in cats:
                    launchers.append(activity.get(ANDROID+'name'))
        self.assertEqual(['.FlipHomeActivity'], launchers)

    def test_home_does_not_route_through_setup_or_shizuku(self):
        source = (ROOT / 'app/src/main/java/org/duofold/live/FlipHomeActivity.kt').read_text()
        self.assertNotIn('FirstRun.required', source)
        self.assertNotIn('SetupActivity', source)
        self.assertIn('mutableStateOf("home")', source)
        self.assertIn('openPreview()', source)
        self.assertIn('page = "diagnostics"', source)
        self.assertIn('Switch(checked = false, onCheckedChange = null, enabled = false)', source)

if __name__ == '__main__':
    unittest.main()
