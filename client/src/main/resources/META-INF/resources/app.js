const $ = (s) => document.querySelector(s);

const api = (p, o = {}) =>
  fetch('/api/' + p, o).then(async (r) => {
    const t = await r.text();
    if (!r.ok) throw Error(`HTTP ${r.status}: ${xmlText(t)}`);
    return t;
  });

function xmlText(s) {
  try {
    const d = new DOMParser().parseFromString(s, 'application/xml');
    return d.querySelector('message')?.textContent || s;
  } catch {
    return s;
  }
}

const parse = (s) => new DOMParser().parseFromString(s, 'application/xml');
const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[c]));

function showError(e) {
  $('#message').textContent = e.message;
}

function notice(s) {
  $('#message').textContent = s;
}

function formatOrganizations(xml) {
  const x = parse(xml);
  const items = [...x.documentElement.children].filter((e) => e.tagName === 'organization');
  if (!items.length) return 'Организаций нет';
  return items
    .map((o) => {
      const v = (t) => o.querySelector(t)?.textContent || '—';
      return `#${o.getAttribute('id')} ${v('name')} · ${v('type')} · оборот ${v('annualTurnover')} · ${v('postalAddress street')}`;
    })
    .join('\n');
}

function listPath() {
  const f = new FormData($('#filters'));
  const q = new URLSearchParams();
  for (const [k, v] of f) if (v) q.append(k, v);
  return 'organizations?' + q;
}

async function load() {
  try {
    const x = parse(await api(listPath()));
    const root = x.documentElement;
    const items = [...root.children].filter((e) => e.tagName === 'organization');
    const total = root.querySelector('total')?.textContent || 0;
    const page = root.querySelector('page')?.textContent || 1;
    $('#total').textContent = `Найдено: ${total}; страница ${page}`;

    $('#rows').innerHTML = items
      .map((o) => {
        const id = o.getAttribute('id');
        const v = (t) => o.querySelector(t)?.textContent || '—';
        return `<tr><td>${esc(id)}</td><td>${esc(v('name'))}</td><td>${esc(v('creationDate'))}</td>` +
          `<td>${esc(v('annualTurnover'))}</td><td>${esc(v('type'))}</td><td>${esc(v('postalAddress street'))}</td>` +
          `<td><button data-emps="${esc(id)}">Список</button> <button data-add="${esc(id)}">Добавить</button></td>` +
          `<td><button data-delete="${esc(id)}">Удалить</button></td></tr>`;
      })
      .join('');

    $('#rows').querySelectorAll('[data-delete]').forEach((b) => {
      b.onclick = async () => {
        try {
          await api('organizations/' + b.dataset.delete, { method: 'DELETE' });
          notice('Организация удалена');
          load();
        } catch (e) {
          showError(e);
        }
      };
    });

    $('#rows').querySelectorAll('[data-emps]').forEach((b) => {
      b.onclick = async () => {
        try {
          const t = await api(`organizations/${b.dataset.emps}/employees`);
          notice(`Сотрудники организации ${b.dataset.emps}:\n${xmlText(t)}`);
        } catch (e) {
          showError(e);
        }
      };
    });

    $('#rows').querySelectorAll('[data-add]').forEach((b) => {
      b.onclick = async () => {
        const name = prompt('Имя сотрудника');
        if (!name) return;
        try {
          await api(`organizations/${b.dataset.add}/employees`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/xml' },
            body: `<employee><name>${esc(name)}</name></employee>`,
          });
          notice('Сотрудник добавлен');
        } catch (e) {
          showError(e);
        }
      };
    });
  } catch (e) {
    showError(e);
  }
}

$('#filters').onsubmit = (e) => {
  e.preventDefault();
  load();
};

$('#prev').onclick = () => {
  const p = $('[name=page]');
  p.value = Math.max(1, +p.value - 1);
  load();
};

$('#next').onclick = () => {
  $('[name=page]').stepUp();
  load();
};

$('#create').onsubmit = async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const f = new FormData(form);
  const n = (k) => esc(f.get(k));
  const turnover = n('annualTurnover');
  const full = n('fullName');
  const body = `<organization><name>${n('name')}</name>` +
    `<coordinates><x>${n('x')}</x><y>${n('y')}</y></coordinates>` +
    (turnover ? `<annualTurnover>${turnover}</annualTurnover>` : '') +
    (full ? `<fullName>${full}</fullName>` : '') +
    `<type>${n('type')}</type><postalAddress><street>${n('street')}</street></postalAddress></organization>`;
  try {
    await api('organizations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/xml' },
      body,
    });
    notice('Организация создана');
    form.reset();
    load();
  } catch (err) {
    showError(err);
  }
};

$('#employee-filter').onsubmit = async (e) => {
  e.preventDefault();
  const f = new FormData(e.currentTarget);
  try {
    const xml = await api(`directory/filter/employees/${f.get('min')}/${f.get('max')}`);
    notice(`Организации по числу сотрудников:\n${formatOrganizations(xml)}`);
  } catch (err) {
    showError(err);
  }
};

$('#directory-sort').onsubmit = async (e) => {
  e.preventDefault();
  const f = new FormData(e.currentTarget);
  const desc = f.has('desc');
  try {
    const xml = await api(`directory/order/${encodeURIComponent(f.get('field'))}/${desc}`);
    notice(`Результат сортировки:\n${formatOrganizations(xml)}`);
  } catch (err) {
    showError(err);
  }
};

$('#grouped').onclick = async () => {
  try {
    const x = parse(await api('organizations/stats/grouped-by-name'));
    const lines = [...x.querySelectorAll('group')].map(
      (g) => `${g.querySelector('name')?.textContent}: ${g.querySelector('count')?.textContent}`
    );
    notice(lines.join('\n') || 'Нет данных');
  } catch (e) {
    showError(e);
  }
};

$('#less').onclick = async () => {
  try {
    const x = parse(await api(`organizations/stats/annual-turnover/less-than/${$('#turnover').value}`));
    notice(`Организаций с меньшим оборотом: ${x.querySelector('count')?.textContent}`);
  } catch (e) {
    showError(e);
  }
};

$('#unique').onclick = async () => {
  try {
    const x = parse(await api('organizations/stats/annual-turnover/unique'));
    const values = [...x.querySelectorAll('value')].map(
      (v) => (['true', '1'].includes(v.getAttributeNS('http://www.w3.org/2001/XMLSchema-instance', 'nil')) ? 'null' : v.textContent)
    );
    notice('Уникальные значения оборота: ' + values.join(', '));
  } catch (e) {
    showError(e);
  }
};

$('#console').onsubmit = async (e) => {
  e.preventDefault();
  const f = new FormData(e.currentTarget);
  const method = f.get('method');
  const path = f.get('path').replace(/^\/+/, '');
  const body = f.get('body');
  const options = { method };
  if (['POST', 'PUT'].includes(method)) {
    options.headers = { 'Content-Type': 'application/xml' };
    options.body = body;
  }
  try {
    const result = await api(path, options);
    $('#console-result').textContent = result || 'Операция выполнена успешно (пустой ответ)';
  } catch (err) {
    $('#console-result').textContent = err.message;
  }
};

load();
