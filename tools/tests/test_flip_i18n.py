"""Locale parity and source separation checks; not Android UI tests."""
import pathlib, re, unittest, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[2]
class FlipResources(unittest.TestCase):
 def test_locales_and_placeholders_match(self):
  res = ROOT/'app/src/main/res'
  def read(path): return {e.attrib['name']: ''.join(e.itertext()) for e in ET.parse(path).getroot()}
  en=read(res/'values/flip_strings.xml'); ko=read(res/'values-ko/flip_strings.xml')
  self.assertEqual(set(en),set(ko) - {"service_description"})
  for key in en:
   pattern=r'%\d+\$(?:\.\d+)?[sdf]'
   self.assertCountEqual(re.findall(pattern,en[key]),re.findall(pattern,ko[key]),key)
 def test_live_mode_does_not_fall_back_to_virtual_angle(self):
  s=(ROOT/'app/src/main/java/org/duofold/live/FlipPreviewActivity.kt').read_text()
  self.assertIn('val angle = if (live) observed else manual',s)
  self.assertIn('getString(R.string.flip_waiting_angle)',s)
  self.assertNotIn('angle.roundToInt()',s)
