import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from xml.dom import minidom

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from yaml_to_xml import load_yaml, convert_document as export_xml
from xml_to_yaml import convert_document as import_xml
from sync_xml_to_yaml import sync_document
import render_dec_graph as renderer
sys.path.pop(0)

class BusinessDataSourceTest(unittest.TestCase):
    def source(self):
        return load_yaml(ROOT / 'examples/business-order-payment.yaml')

    def test_roundtrip_preserves_business_action_and_back_sources(self):
        source = self.source()
        restored = import_xml(minidom.parseString(export_xml(source, emit_id_comments=True)))
        self.assertEqual(source['business'], restored['business'])

    def test_xml_sync_updates_sources_without_losing_identity(self):
        source = self.source()
        edited = minidom.parseString(export_xml(source, emit_id_comments=True))
        edited.documentElement.setAttribute('dataSource', 'new-default')
        edited.getElementsByTagName('action')[0].setAttribute('dataSource', 'override')
        merged, report = sync_document(source, edited)
        self.assertEqual('READY', report['status'])
        self.assertEqual('new-default', merged['business']['dataSource'])
        self.assertEqual('override', merged['business']['directories'][0]['actions'][0]['dataSource'])
        self.assertEqual(source['business']['id'], merged['business']['id'])

    def test_unknown_sources_fail_validation_including_back_and_empty_config(self):
        for location in ['business', 'action', 'back']:
            with self.subTest(location=location), tempfile.TemporaryDirectory() as td:
                source = self.source(); business = source['business']
                target = business if location == 'business' else business['directories'][1]['actions'][0] if location == 'action' else business['directories'][2]['subDirectories'][0]['back']['actions'][0]
                target['dataSource'] = 'unknown-source'
                for name, document in [('business', source), ('config', {'kind':'config', 'dataSources':[]})]:
                    subprocess.run([sys.executable, str(ROOT/'scripts/yaml_io.py'), 'write', str(Path(td)/f'{name}.yaml'), '--from-json', '-'], input=json.dumps(document), text=True, capture_output=True, check=True)
                result = subprocess.run([sys.executable, str(ROOT/'scripts/validate_dec_yaml.py'), td], text=True, capture_output=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('unknown-source', result.stdout + result.stderr)

    def test_effective_sources_render_with_links_and_origin_including_back(self):
        docs = renderer.load_docs(renderer.discover([str(ROOT.parents[1]/'demo/order-payment-yaml')]))
        cat = renderer.build_catalog(docs); business = cat['businesses'][0]
        pages = renderer.page_names(Path('directories.html'), cat['systems'])
        html = renderer.render_business_map_detail(business, cat, pages, docs)
        self.assertIn('流程默认 DataSource', html)
        self.assertIn('DS: paymentService', html)
        self.assertIn('Back returnPaying', html)
        self.assertIn('datasources.html#', html)
        action = business['directories'][0]['actions'][0]
        self.assertTrue(all(origin == 'RuleView' for _, _, origin in renderer.action_data_sources(action, business, cat)))
        for action in [business['directories'][1]['actions'][0], business['directories'][4]['actions'][0]]:
            self.assertTrue(all(origin == 'Action' for _, _, origin in renderer.action_data_sources(action, business, cat)))
        action = {'name':'work', 'ruleRef':'test'}
        rules = {'ruleviews':{'test':{'dataSource':'view', 'rules':[{'name':'one','dataSource':'rule'}, {'name':'two'}]}}}
        self.assertEqual([('one','rule','Rule'),('two','view','RuleView')], renderer.action_data_sources(action,business,rules))
        rules['ruleviews']['test'].pop('dataSource')
        self.assertEqual([('one','rule','Rule'),('two','data1','Business')], renderer.action_data_sources(action,business,rules))
        action['dataSource']='override'
        self.assertTrue(all(source == 'override' for _,source,_ in renderer.action_data_sources(action,business,rules)))
