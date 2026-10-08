#!/usr/bin/env python3
"""Execute every explicit XML example against the disposable Docker Compose stack.
Never accepts a remote URL or database: mutations/faults target local Compose only.
Requires PyYAML (see ../requirements.txt).
"""
import copy
import http.server
import json
import os
import re
from pathlib import Path
import ssl
import subprocess
import threading
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import yaml

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ['docker', 'compose', '-f', str(ROOT / 'wildfly/compose.yaml')]
PORT = int(os.environ.get('SOA_TEST_HTTPS_PORT', '62811'))
BASE = f'https://localhost:{PORT}'
CONTEXT = ssl._create_unverified_context()
SPECS = {n: yaml.safe_load((ROOT / f'openapi/{n}-service.yaml').read_text()) for n in ('organization', 'orgdirectory')}
EXAMPLES = {}
CHECKED = set()
REPORT = []
OPERATIONS = {(service, path, method.upper()) for service, spec in SPECS.items()
              for path, operations in spec['paths'].items() for method in operations
              if method in {'get', 'post', 'put', 'delete', 'patch', 'head', 'options'}}
EXERCISED = set()


def inventory(value, key):
    if isinstance(value, dict):
        for k, v in value.items():
            if k == 'example':
                EXAMPLES[key + '/example'] = v
            elif k == 'examples':
                for name, example in v.items():
                    EXAMPLES[key + '/examples/' + name] = example['value']
            else:
                inventory(v, key + '/' + k)
    elif isinstance(value, list):
        for i, v in enumerate(value):
            inventory(v, key + '/' + str(i))


for service, spec in SPECS.items():
    inventory(spec, service)


def example(service, fragment):
    matches = [(k, v) for k, v in EXAMPLES.items() if k.startswith(service + '/') and fragment in k]
    assert len(matches) == 1, matches
    return matches[0]


def command(*args):
    return subprocess.check_output(COMPOSE + list(args), text=True).strip()


def sql(query):
    return command('exec', '-T', 'db', 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'soa', '-d', 'soa', '-Atc', query)


def request(method, path, body=None, expected=200, content_type='application/xml'):
    req = urllib.request.Request(BASE + path, data=body.encode() if body is not None else None,
                                 method=method, headers={'Accept': 'application/xml', 'Content-Type': content_type})
    try:
        response = urllib.request.urlopen(req, context=CONTEXT, timeout=45)
    except urllib.error.HTTPError as error:
        response = error
    data = response.read().decode()
    for service, template, operation in OPERATIONS:
        route = ('/orgdirectory' if service == 'orgdirectory' else '') + template
        if operation == method and re.fullmatch(re.sub(r'\{[^}]+\}', '[^/]+', route), path.split('?')[0]):
            EXERCISED.add((service, template, operation))
    assert response.status == expected, (method, path, expected, response.status, data)
    if expected == 204:
        assert not data
        return None
    assert 'xml' in response.headers.get('Content-Type', ''), (path, response.headers)
    return ET.fromstring(data)


def ready():
    deadline = time.monotonic() + 180
    while True:
        try:
            request('GET', '/organizations')
            request('GET', '/orgdirectory/order/name/false')
            return
        except Exception:
            if time.monotonic() >= deadline:
                raise
            time.sleep(1)


def reset():
    sql('TRUNCATE s389491.employees, s389491.organizations RESTART IDENTITY CASCADE')


def canonical(node):
    # XML declaration, insignificant whitespace, namespace prefix choice and list order
    # do not change the represented data. Numeric serializers may use 75000.0.
    value = (node.text or '').strip()
    if node.tag in {'salary', 'x'} and value:
        value = str(float(value))
    return (node.tag, tuple(sorted(node.attrib.items())), value, tuple(sorted(canonical(c) for c in node)))


def check(key, actual, generated=False):
    expected = ET.fromstring(EXAMPLES[key])
    if generated:
        actual = copy.deepcopy(actual)
        for node in actual.iter('creationDate'):
            # Dates are generated at creation, unlike the illustrative sample date.
            assert node.text == time.strftime('%Y-%m-%d')
            node.text = '2024-03-01'
    assert canonical(actual) == canonical(expected), (key, ET.tostring(actual).decode(), EXAMPLES[key])
    CHECKED.add(key)
    REPORT.append({'example': key, 'status': 'passed'})
    print('PASS', key, flush=True)


ORG_KEY, ORG_XML = example('organization', '/organizations/post/requestBody/')
EMP_KEY, EMP_XML = example('organization', '/employees/post/requestBody/')


def create(name='Acme', turnover='1000000'):
    node = ET.fromstring(ORG_XML)
    node.find('name').text = name
    if turnover is None:
        node.remove(node.find('annualTurnover'))
    else:
        node.find('annualTurnover').text = str(turnover)
    return request('POST', '/organizations', ET.tostring(node, encoding='unicode'), 201)


def component(service, name):
    return example(service, '/components/responses/' + name + '/')[0]


def cli(operation):
    return command('exec', '-T', 'app', '/opt/jboss/wildfly/bin/jboss-cli.sh', '--connect', '--command=' + operation)


def upstream(url):
    result = cli('/system-property=organization-service.url:write-attribute(name=value,value="' + url + '")')
    assert '"outcome" => "success"' in result, result
    result = cli('/deployment=orgdirectory.war:redeploy')
    assert '"outcome" => "success"' in result, result


# Guard against an SSH tunnel or arbitrary service on the test port before ANY writes.
port_mapping = command('port', 'app', '61811')
assert port_mapping == f'127.0.0.1:{PORT}', port_mapping
assert sql('SELECT current_database(), current_user') == 'soa|soa'
ready()
for service, path in [('organization', 'organizations'), ('orgdirectory', 'orgdirectory')]:
    with urllib.request.urlopen(BASE + '/openapi/' + path + '?format=JSON', context=CONTEXT) as response:
        live = json.load(response)
    assert live['servers'] == SPECS[service]['servers'], live['servers']
    assert all('description' not in server for server in live['servers'])
    for route, operations in SPECS[service]['paths'].items():
        for method, operation in operations.items():
            if not isinstance(operation, dict) or 'requestBody' not in operation:
                continue
            expected = operation['requestBody']['content']['application/xml']
            actual = live['paths'][route][method]['requestBody']['content']['application/xml']
            assert actual == expected, ('Scanner changed XML request contract', route, method, actual)

reset()
created = request('POST', '/organizations', ORG_XML, 201)
assert created.attrib['id'] == '1'
for child in ET.fromstring(ORG_XML):
    assert canonical(created.find(child.tag)) == canonical(child)
CHECKED.add(ORG_KEY)
REPORT.append({'example': ORG_KEY, 'status': 'passed'})
print('PASS', ORG_KEY, flush=True)
check(example('organization', '/organizations/get/responses/200/')[0], request('GET', '/organizations'), generated=True)

employee = request('POST', '/organizations/1/employees', EMP_XML, 201)
assert employee.attrib == {'id': '1', 'organizationId': '1'}
for child in ET.fromstring(EMP_XML):
    assert canonical(employee.find(child.tag)) == canonical(child)
CHECKED.add(EMP_KEY)
REPORT.append({'example': EMP_KEY, 'status': 'passed'})
print('PASS', EMP_KEY, flush=True)
request('GET', '/organizations/1/employees')
request('GET', '/organizations/1')
request('PUT', '/organizations/1', ORG_XML)
check(example('orgdirectory', '/responses/200/')[0], request('GET', '/orgdirectory/filter/employees/1/1'), generated=True)
for field in SPECS['orgdirectory']['paths']['/order/{param-name}/{desc}']['get']['parameters'][0]['schema']['enum']:
    for direction in ('true', 'false'):
        assert request('GET', f'/orgdirectory/order/{field}/{direction}').findtext('total') == '1'
request('DELETE', '/organizations/1/employees/1', expected=204)
request('DELETE', '/organizations/1', expected=204)

reset()
for name in ['Acme', 'Acme', 'Acme', 'Globex']:
    create(name)
check(example('organization', '/grouped-by-name/get/responses/200/')[0], request('GET', '/organizations/stats/grouped-by-name'))
reset()
for _ in range(5):
    create()
check(example('organization', '/less-than/{value}/get/responses/200/')[0], request('GET', '/organizations/stats/annual-turnover/less-than/1000001'))
reset()
for turnover in [1000000, 2500000, None]:
    create(turnover=turnover)
check(example('organization', '/unique/get/responses/200/')[0], request('GET', '/organizations/stats/annual-turnover/unique'))
check(component('organization', 'BadRequest'), request('POST', '/organizations', ORG_XML.replace('<name>Acme</name>', '<name></name>'), 400))
check(component('organization', 'UnsupportedMediaType'), request('POST', '/organizations', '{}', 415, 'application/json'))
check(example('organization', '/examples/endpointNotFound')[0], request('GET', '/not-an-endpoint', expected=404))
check(example('organization', '/examples/itemNotFound')[0], request('GET', '/organizations/2147483647', expected=404))
check(component('orgdirectory', 'EndpointNotFound'), request('GET', '/orgdirectory/not-an-endpoint', expected=404))

# A missing relation is an internal error, not a client validation error.
try:
    sql('ALTER TABLE s389491.organizations RENAME TO organizations_example_backup')
    check(component('organization', 'InternalServerError'), request('GET', '/organizations', expected=500))
finally:
    sql('ALTER TABLE s389491.organizations_example_backup RENAME TO organizations')

# Controlled outage of the disposable database only.
try:
    command('stop', 'db')
    check(component('organization', 'ServiceUnavailable'), request('GET', '/organizations', expected=503))
finally:
    command('start', 'db')
ready()

# Controlled upstream errors/timeouts, without production fault-injection endpoints.
class FaultServer(http.server.BaseHTTPRequestHandler):
    delay = False
    def do_GET(self):
        if FaultServer.delay:
            time.sleep(14)
        self.send_response(500)
        self.end_headers()
    def log_message(self, *args):
        pass

server = http.server.ThreadingHTTPServer(('0.0.0.0', 0), FaultServer)
threading.Thread(target=server.serve_forever, daemon=True).start()
app_id = command('ps', '-q', 'app')
networks = json.loads(subprocess.check_output(['docker', 'inspect', app_id], text=True))[0]['NetworkSettings']['Networks']
gateway = next(iter(networks.values()))['Gateway']
# The fault-injection server runs on the host. host.docker.internal reaches the
# host from inside the container on Docker Desktop (macOS/Windows) and on Linux
# with extra_hosts resolved via host-gateway in the compose file. The plain
# bridge gateway is not routed back to the host on Docker Desktop.
host = 'host.docker.internal'
try:
    upstream(f'http://{host}:{server.server_port}')
    for code in (502, 504):
        FaultServer.delay = code == 504
        for path, template in [('/filter/employees/0/10', '/filter/employees/{min-employees-count}/{max-employees-count}'), ('/order/name/false', '/order/{param-name}/{desc}')]:
            key = example('orgdirectory', '/paths/' + template + '/get/responses/' + str(code) + '/')[0]
            check(key, request('GET', '/orgdirectory' + path, expected=code))
finally:
    upstream('https://localhost:61811')
    server.shutdown()
ready()
assert CHECKED == set(EXAMPLES), ('Unverified examples', sorted(set(EXAMPLES) - CHECKED))
assert EXERCISED == OPERATIONS, ('Unexercised operations', OPERATIONS - EXERCISED)
report_path = ROOT / 'e2e-tests/build/reports/openapi-examples.json'
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps({'passed': len(CHECKED), 'total': len(EXAMPLES), 'examples': REPORT, 'operations': len(EXERCISED)}, indent=2))
print(f'All {len(CHECKED)} OpenAPI examples passed; all {len(EXERCISED)} operations exercised.', flush=True)
