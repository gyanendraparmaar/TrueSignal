(function () {
  const api = window.TrueSignalAPI;
  const charts = window.TrueSignalCharts;

  const state = {
    currentView: 'dashboard',
    detailMonitorId: null,
    uptimeChart: null,
    responseChart: null,
    sse: null,
  };

  function formatDate(instant) {
    if (instant == null) return '—';
    try {
      const d = new Date(instant);
      if (Number.isNaN(d.getTime())) return '—';
      return d.toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      });
    } catch (_) {
      return '—';
    }
  }

  function timeAgo(instant) {
    if (instant == null) return '—';
    const t = new Date(instant).getTime();
    if (Number.isNaN(t)) return '—';
    let s = Math.floor((Date.now() - t) / 1000);
    if (s < 0) s = 0;
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
  }

  function formatDuration(sec) {
    if (sec == null || sec < 0) return '—';
    const s = Number(sec);
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    return `${h}h ${m}m`;
  }

  function statusBadge(status) {
    const s = (status || 'UNKNOWN').toString().toUpperCase();
    let cls = 'badge--unknown';
    if (s === 'UP') cls = 'badge--up';
    else if (s === 'DOWN') cls = 'badge--down';
    else if (s === 'DEGRADED') cls = 'badge--degraded';
    return `<span class="badge ${cls}">${s}</span>`;
  }

  function monitorStatusHtml(m) {
    if (m.paused) {
      return '<span class="badge badge--paused">Paused</span>';
    }
    return statusBadge(m.currentStatus);
  }

  function incidentStatusBadge(st) {
    const s = (st || '').toString().toUpperCase();
    if (s === 'ONGOING') return '<span class="badge badge--danger">Ongoing</span>';
    if (s === 'RESOLVED') return '<span class="badge badge--up">Resolved</span>';
    return `<span class="badge badge--unknown">${s}</span>`;
  }

  function diagnosisSourceTag(source) {
    if (!source) return '';
    const s = source.toUpperCase();
    if (s === 'AI') return '<span class="source-tag source-tag--ai">AI</span>';
    return '<span class="source-tag source-tag--rule">Rule</span>';
  }

  function diagnosisInline(incident) {
    if (!incident.diagnosis) return '';
    return `<div class="incidents-diagnosis-inline">${diagnosisSourceTag(incident.diagnosisSource)} ${escapeHtml(incident.diagnosis)}</div>`;
  }

  function buildDiagnosisCard(incident) {
    if (!incident.diagnosis) return '';
    const conf = (incident.diagnosisConfidence || 'LOW').toUpperCase();
    const confClass = conf === 'HIGH' ? 'high' : conf === 'MEDIUM' ? 'medium' : 'low';
    const isAi = (incident.diagnosisSource || '').toUpperCase() === 'AI';
    const iconClass = isAi ? 'diagnosis-icon--ai' : 'diagnosis-icon--rule';
    const iconLabel = isAi ? 'AI' : 'R';
    const category = (incident.diagnosisCategory || 'UNKNOWN').replace(/_/g, ' ');

    let html = `<div class="diagnosis-card diagnosis-card--${confClass}">`;
    html += `<div class="diagnosis-header">`;
    html += `<div class="diagnosis-icon ${iconClass}">${iconLabel}</div>`;
    html += `<div class="diagnosis-title">${escapeHtml(incident.diagnosis)}</div>`;
    html += `</div>`;
    html += `<div class="diagnosis-badges">`;
    html += `<span class="diagnosis-badge diagnosis-badge--category">${escapeHtml(category)}</span>`;
    html += `<span class="diagnosis-badge diagnosis-badge--confidence-${confClass}">${escapeHtml(conf)} confidence</span>`;
    html += `<span class="diagnosis-badge diagnosis-badge--source">${isAi ? 'AI-Powered' : 'Rule-Based'}</span>`;
    html += `</div>`;
    if (incident.diagnosisExplanation) {
      html += `<div class="diagnosis-section"><div class="diagnosis-section-label">Analysis</div>`;
      html += `<div class="diagnosis-section-text">${escapeHtml(incident.diagnosisExplanation)}</div></div>`;
    }
    if (incident.diagnosisSuggestion) {
      html += `<div class="diagnosis-section"><div class="diagnosis-section-label">Suggested action</div>`;
      html += `<div class="diagnosis-suggestion diagnosis-section-text">${escapeHtml(incident.diagnosisSuggestion)}</div></div>`;
    }
    html += `</div>`;
    return html;
  }

  function emptyStateHtml(message, iconPath) {
    const icon = iconPath || 'M9 17h6M12 17v4M4 7l4.5 4.5M20 7l-4.5 4.5M12 2v3M4.22 12H2M22 12h-2.22M12 12a4 4 0 100-8 4 4 0 000 8z';
    return `<div class="empty-state"><svg class="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="${icon}"/></svg><div class="empty-state-text">${message}</div></div>`;
  }

  function animateValue(el, end) {
    if (!el) return;
    const start = parseInt(el.textContent) || 0;
    if (start === end) { el.textContent = end; return; }
    const duration = 400;
    const startTime = performance.now();
    function step(now) {
      const progress = Math.min((now - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      el.textContent = Math.round(start + (end - start) * eased);
      if (progress < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  function showToast(message, isError) {
    const c = document.getElementById('toast-container');
    if (!c) return;
    const el = document.createElement('div');
    el.className = 'toast' + (isError ? ' toast--error' : '');
    el.textContent = message;
    c.appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(8px)';
      el.style.transition = 'all 0.25s ease';
      setTimeout(() => el.remove(), 300);
    }, 4500);
  }

  function setSseStatus(connected) {
    const dot = document.getElementById('sse-status-dot');
    const text = document.getElementById('sse-status-text');
    if (!dot || !text) return;
    dot.classList.remove('live', 'reconnecting');
    if (connected === true) {
      dot.classList.add('live');
      text.textContent = 'Live';
    } else if (connected === 'reconnecting') {
      dot.classList.add('reconnecting');
      text.textContent = 'Reconnecting…';
    } else {
      text.textContent = 'Offline';
    }
  }

  function destroyUptimeChart() {
    if (state.uptimeChart) {
      state.uptimeChart.destroy();
      state.uptimeChart = null;
    }
  }

  function destroyResponseChart() {
    if (state.responseChart) {
      state.responseChart.destroy();
      state.responseChart = null;
    }
  }

  function parseHash() {
    const h = window.location.hash.replace(/^#/, '');
    const m = /^monitor\/(\d+)$/.exec(h);
    if (m) return { view: 'monitor-detail', id: Number(m[1]) };
    return null;
  }

  function setHashForView(view, monitorId) {
    if (view === 'monitor-detail' && monitorId != null) {
      window.location.hash = `monitor/${monitorId}`;
    } else if (view === 'dashboard') {
      if (window.location.hash) {
        history.replaceState(null, '', window.location.pathname + window.location.search);
      }
    } else {
      window.location.hash = view;
    }
  }

  function showView(viewName, options) {
    const opts = options || {};
    document.querySelectorAll('.view').forEach((v) => v.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach((n) => n.classList.remove('active'));

    const map = {
      dashboard: 'dashboard-view',
      monitors: 'monitors-view',
      'monitor-detail': 'monitor-detail-view',
      cluster: 'cluster-view',
      incidents: 'incidents-view',
      status: 'status-view',
    };

    const id = map[viewName];
    if (id) {
      const el = document.getElementById(id);
      if (el) el.classList.add('active');
    }

    const navSel = {
      dashboard: '[data-nav="dashboard"]',
      monitors: '[data-nav="monitors"]',
      cluster: '[data-nav="cluster"]',
      incidents: '[data-nav="incidents"]',
      status: '[data-nav="status"]',
    };
    if (viewName === 'monitor-detail') {
      const btn = document.querySelector('[data-nav="monitors"]');
      if (btn) btn.classList.add('active');
    } else {
      const sel = navSel[viewName];
      if (sel) {
        const btn = document.querySelector(sel);
        if (btn) btn.classList.add('active');
      }
    }

    state.currentView = viewName;

    if (viewName !== 'dashboard') {
      destroyUptimeChart();
    }
    if (viewName !== 'monitor-detail') {
      destroyResponseChart();
      state.detailMonitorId = null;
    }

    if (viewName === 'monitor-detail' && opts.monitorId != null) {
      state.detailMonitorId = opts.monitorId;
      loadMonitorDetail(opts.monitorId);
    } else if (viewName === 'dashboard') {
      loadDashboard();
    } else if (viewName === 'monitors') {
      loadMonitorsView();
    } else if (viewName === 'cluster') {
      loadCluster();
    } else if (viewName === 'incidents') {
      loadProjects();
      loadIncidents();
    } else if (viewName === 'status') {
      loadProjects();
    }
  }

  async function loadDashboard() {
    try {
      const data = await api.apiGet('/api/dashboard/overview');
      animateValue(document.getElementById('stat-monitors-up'), data.monitorsUp ?? 0);
      animateValue(document.getElementById('stat-monitors-down'), data.monitorsDown ?? 0);
      animateValue(document.getElementById('stat-active-incidents'), data.activeIncidents ?? 0);
      animateValue(document.getElementById('stat-nodes-alive'), data.nodesAlive ?? 0);
      document.getElementById('dash-total-monitors').textContent = data.totalMonitors ?? '0';
      document.getElementById('dash-monitors-paused').textContent = data.monitorsPaused ?? '0';
      document.getElementById('dash-total-nodes').textContent = data.totalNodes ?? '0';

      destroyUptimeChart();
      state.uptimeChart = charts.createUptimeGauge(
        'dashboard-uptime-gauge',
        data.overallUptimePercent ?? 0
      );

      const tbody = document.querySelector('#dashboard-monitors-table tbody');
      const empty = document.getElementById('dashboard-monitors-empty');
      const table = document.getElementById('dashboard-monitors-table');
      const monitors = data.monitors || [];
      if (!tbody) return;
      tbody.innerHTML = '';
      if (monitors.length === 0) {
        if (empty) { empty.innerHTML = emptyStateHtml('No monitors yet — add one to get started.', 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'); empty.style.display = 'block'; }
        if (table) table.style.display = 'none';
      } else {
        if (empty) empty.style.display = 'none';
        if (table) table.style.display = '';
        monitors.forEach((m) => {
          const tr = document.createElement('tr');
          tr.className = 'clickable-row';
          tr.dataset.id = m.id;
          tr.innerHTML = `
            <td>${escapeHtml(m.name)}</td>
            <td><span class="url-ellipsis mono">${escapeHtml(m.url || '')}</span></td>
            <td>${escapeHtml((m.type || '').toString())}</td>
            <td>${monitorStatusHtml(m)}</td>
            <td class="mono">${(m.uptimePercent != null ? m.uptimePercent.toFixed(2) : '—')}%</td>
            <td>${timeAgo(m.lastCheckedAt)}</td>
          `;
          tr.addEventListener('click', () => {
            setHashForView('monitor-detail', m.id);
            showView('monitor-detail', { monitorId: m.id });
          });
          tbody.appendChild(tr);
        });
      }
    } catch (e) {
      showToast(e.message || 'Failed to load dashboard', true);
    }
  }

  async function loadMonitorsView() {
    try {
      const list = await api.apiGet('/api/monitors');
      const tbody = document.querySelector('#monitors-table tbody');
      const empty = document.getElementById('monitors-empty');
      const table = document.getElementById('monitors-table');
      tbody.innerHTML = '';
      if (!list || list.length === 0) {
        if (empty) { empty.innerHTML = emptyStateHtml('No monitors configured — click "Add monitor" to begin.', 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'); empty.style.display = 'block'; }
        if (table) table.style.display = 'none';
        return;
      }
      if (empty) empty.style.display = 'none';
      if (table) table.style.display = '';
      list.forEach((m) => {
        const tr = document.createElement('tr');
        tr.className = 'clickable-row';
        tr.dataset.id = m.id;
        tr.innerHTML = `
          <td>${escapeHtml(m.name)}</td>
          <td><span class="url-ellipsis mono">${escapeHtml(m.url || '')}</span></td>
          <td class="mono">${m.intervalSeconds ?? '—'}s</td>
          <td>${monitorStatusHtml(m)}</td>
          <td class="mono">${(m.uptimePercent != null ? m.uptimePercent.toFixed(2) : '—')}%</td>
        `;
        tr.addEventListener('click', () => {
          setHashForView('monitor-detail', m.id);
          showView('monitor-detail', { monitorId: m.id });
        });
        tbody.appendChild(tr);
      });
    } catch (e) {
      showToast(e.message || 'Failed to load monitors', true);
    }
  }

  async function loadMonitorDetail(id) {
    destroyResponseChart();
    try {
      const [monitor, checks, incidents] = await Promise.all([
        api.apiGet(`/api/monitors/${id}`),
        api.apiGet(`/api/monitors/${id}/checks?page=0&size=50`),
        api.apiGet(`/api/monitors/${id}/incidents`),
      ]);

      document.getElementById('detail-monitor-name').textContent = monitor.name || 'Monitor';
      document.getElementById('detail-monitor-url').textContent = monitor.url || '';

      const pauseBtn = document.getElementById('btn-toggle-pause');
      if (pauseBtn) {
        pauseBtn.textContent = monitor.paused ? 'Resume' : 'Pause';
      }

      const grid = document.getElementById('detail-info-grid');
      grid.innerHTML = `
        <div class="info-tile"><dt>Type</dt><dd>${escapeHtml((monitor.type || '').toString())}</dd></div>
        <div class="info-tile"><dt>Status</dt><dd>${monitorStatusHtml(monitor)}</dd></div>
        <div class="info-tile"><dt>Uptime</dt><dd>${monitor.uptimePercent != null ? monitor.uptimePercent.toFixed(2) + '%' : '—'}</dd></div>
        <div class="info-tile"><dt>Avg response</dt><dd>${monitor.avgResponseTimeMs != null ? monitor.avgResponseTimeMs + ' ms' : '—'}</dd></div>
        <div class="info-tile"><dt>Interval</dt><dd>${monitor.intervalSeconds ?? '—'}s</dd></div>
        <div class="info-tile"><dt>Timeout</dt><dd>${monitor.timeoutMs ?? '—'} ms</dd></div>
        <div class="info-tile"><dt>Expected code</dt><dd>${monitor.expectedStatusCode ?? '—'}</dd></div>
        <div class="info-tile"><dt>Keyword</dt><dd>${escapeHtml(monitor.keyword || '—')}</dd></div>
        <div class="info-tile"><dt>Project</dt><dd>${escapeHtml(monitor.projectSlug || '—')}</dd></div>
        <div class="info-tile"><dt>Last check</dt><dd>${formatDate(monitor.lastCheckedAt)}</dd></div>
      `;

      state.responseChart = charts.createResponseTimeChart('response-time-chart', checks || []);

      const checksBody = document.querySelector('#detail-checks-table tbody');
      const checksEmpty = document.getElementById('detail-checks-empty');
      const checksTable = document.getElementById('detail-checks-table');
      checksBody.innerHTML = '';
      const checkList = checks || [];
      if (checkList.length === 0) {
        checksEmpty.style.display = 'block';
        checksTable.style.display = 'none';
      } else {
        checksEmpty.style.display = 'none';
        checksTable.style.display = '';
        checkList.forEach((c) => {
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td class="mono">${formatDate(c.checkedAt)}</td>
            <td>${escapeHtml(c.nodeRegion || c.nodeId || '—')}</td>
            <td>${statusBadge(c.status)}</td>
            <td class="mono">${c.responseTimeMs ?? '—'}</td>
            <td class="mono">${c.statusCode ?? '—'}</td>
            <td>${escapeHtml(c.error || '—')}</td>
          `;
          checksBody.appendChild(tr);
        });
      }

      const incBody = document.querySelector('#detail-incidents-table tbody');
      const incEmpty = document.getElementById('detail-incidents-empty');
      const incTable = document.getElementById('detail-incidents-table');
      const diagnosisContainer = document.getElementById('detail-diagnosis-container');
      incBody.innerHTML = '';
      if (diagnosisContainer) diagnosisContainer.innerHTML = '';
      const incList = incidents || [];
      if (incList.length === 0) {
        incEmpty.style.display = 'block';
        incTable.style.display = 'none';
      } else {
        incEmpty.style.display = 'none';
        incTable.style.display = '';

        const ongoingWithDiagnosis = incList.find(
          (i) => i.status === 'ONGOING' && i.diagnosis
        );
        if (ongoingWithDiagnosis && diagnosisContainer) {
          diagnosisContainer.innerHTML = buildDiagnosisCard(ongoingWithDiagnosis);
        }

        incList.forEach((i) => {
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td>${formatDate(i.startedAt)}</td>
            <td>${incidentStatusBadge(i.status)}</td>
            <td>${escapeHtml(i.cause || '—')}${diagnosisInline(i)}</td>
            <td class="mono">${formatDuration(i.durationSeconds)}</td>
          `;
          incBody.appendChild(tr);
        });
      }
    } catch (e) {
      showToast(e.message || 'Failed to load monitor', true);
    }
  }

  async function loadCluster() {
    try {
      const nodes = await api.apiGet('/api/nodes');
      const grid = document.getElementById('cluster-grid');
      const empty = document.getElementById('cluster-empty');
      grid.innerHTML = '';
      if (!nodes || nodes.length === 0) {
        empty.innerHTML = emptyStateHtml('No nodes registered — start a monitor node to see it here.', 'M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83');
        empty.style.display = 'block';
        return;
      }
      empty.style.display = 'none';
      nodes.forEach((n) => {
        const st = (n.status || '').toString().toUpperCase();
        const alive = st === 'ALIVE';
        const suspect = st === 'SUSPECT';
        const dead = st === 'DEAD';
        const card = document.createElement('div');
        card.className =
          'node-card ' + (alive ? 'node-card--alive' : suspect ? 'node-card--warning' : 'node-card--dead');
        const badge =
          st === 'ALIVE'
            ? statusBadge('UP')
            : st === 'SUSPECT'
              ? statusBadge('DEGRADED')
              : statusBadge('DOWN');
        const removeBtn = dead
          ? `<button class="btn btn-danger btn-sm node-remove-btn" data-node-id="${escapeHtml(n.nodeId)}">Remove</button>`
          : '';
        card.innerHTML = `
          <div class="node-card-header">
            <div class="node-id mono">${escapeHtml(n.nodeId || '')}</div>
            ${badge}
          </div>
          <div class="node-meta"><strong>Region</strong> ${escapeHtml(n.region || '—')}</div>
          <div class="node-meta"><strong>Address</strong> <span class="mono">${escapeHtml(n.address || '—')}</span></div>
          <div class="node-meta"><strong>Assigned monitors</strong> ${n.assignedMonitors ?? 0}</div>
          <div class="node-meta"><strong>Last heartbeat</strong> ${timeAgo(n.lastHeartbeat)}</div>
          ${removeBtn ? `<div class="node-card-actions">${removeBtn}</div>` : ''}
        `;
        grid.appendChild(card);
      });

      grid.querySelectorAll('.node-remove-btn').forEach((btn) => {
        btn.addEventListener('click', async () => {
          const nodeId = btn.dataset.nodeId;
          if (!confirm(`Remove dead node ${nodeId}?`)) return;
          try {
            await api.apiDelete(`/api/nodes/${nodeId}`);
            showToast('Node removed');
            loadCluster();
            if (state.currentView === 'dashboard') loadDashboard();
          } catch (err) {
            showToast(err.message || 'Failed to remove node', true);
          }
        });
      });
    } catch (e) {
      showToast(e.message || 'Failed to load nodes', true);
    }
  }

  async function loadIncidents() {
    try {
      const filterEl = document.getElementById('incidents-project-filter');
      const projectParam = filterEl && filterEl.value ? `?project=${encodeURIComponent(filterEl.value)}` : '';
      const [active, recent] = await Promise.all([
        api.apiGet(`/api/incidents${projectParam}`),
        api.apiGet(`/api/incidents/recent${projectParam}`),
      ]);

      const diagCards = document.getElementById('incidents-diagnosis-cards');
      if (diagCards) {
        const diagnosed = (active || []).filter((i) => i.diagnosis);
        diagCards.innerHTML = diagnosed.map((i) => buildDiagnosisCard(i)).join('');
      }

      const aBody = document.querySelector('#active-incidents-table tbody');
      const aEmpty = document.getElementById('active-incidents-empty');
      const aTable = document.getElementById('active-incidents-table');
      aBody.innerHTML = '';
      const alist = active || [];
      if (alist.length === 0) {
        aEmpty.innerHTML = emptyStateHtml('All clear — no active incidents.', 'M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3');
        aEmpty.style.display = 'block';
        aTable.style.display = 'none';
      } else {
        aEmpty.style.display = 'none';
        aTable.style.display = '';
        alist.forEach((i) => {
          const tr = document.createElement('tr');
          tr.className = 'clickable-row';
          tr.innerHTML = `
            <td>${escapeHtml(i.monitorName || '')}</td>
            <td>${formatDate(i.startedAt)}</td>
            <td>${escapeHtml(i.cause || '—')}${diagnosisInline(i)}</td>
            <td class="mono">${formatDuration(i.durationSeconds)}</td>
          `;
          tr.addEventListener('click', () => {
            if (i.monitorId != null) {
              setHashForView('monitor-detail', i.monitorId);
              showView('monitor-detail', { monitorId: i.monitorId });
            }
          });
          aBody.appendChild(tr);
        });
      }

      const rBody = document.querySelector('#recent-incidents-table tbody');
      const rEmpty = document.getElementById('recent-incidents-empty');
      const rTable = document.getElementById('recent-incidents-table');
      rBody.innerHTML = '';
      const rlist = recent || [];
      if (rlist.length === 0) {
        rEmpty.innerHTML = emptyStateHtml('No recent incidents to show.', 'M12 8v4l3 3 M3 12a9 9 0 1018 0 9 9 0 00-18 0z');
        rEmpty.style.display = 'block';
        rTable.style.display = 'none';
      } else {
        rEmpty.style.display = 'none';
        rTable.style.display = '';
        rlist.forEach((i) => {
          const tr = document.createElement('tr');
          tr.className = 'clickable-row';
          tr.innerHTML = `
            <td>${escapeHtml(i.monitorName || '')}</td>
            <td>${formatDate(i.startedAt)}</td>
            <td>${formatDate(i.resolvedAt)}</td>
            <td class="mono">${formatDuration(i.durationSeconds)}</td>
          `;
          tr.addEventListener('click', () => {
            if (i.monitorId != null) {
              setHashForView('monitor-detail', i.monitorId);
              showView('monitor-detail', { monitorId: i.monitorId });
            }
          });
          rBody.appendChild(tr);
        });
      }
    } catch (e) {
      showToast(e.message || 'Failed to load incidents', true);
    }
  }

  function escapeHtml(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
  }

  function renderStatusPreview(data) {
    const wrap = document.getElementById('status-preview-wrap');
    const el = document.getElementById('status-preview');
    if (!wrap || !el) return;
    wrap.style.display = 'block';

    const monitors = (data.monitors || []).map((m) => {
      const daily = m.dailyUptime || {};
      const allEntries = Object.entries(daily).sort((a, b) => a[0].localeCompare(b[0]));
      const entries = allEntries.slice(-30);

      const bars = entries
        .map(([day, val]) => {
          if (val === null || val === undefined) {
            return `<div class="uptime-bar-wrap"><div class="uptime-bar uptime-bar--nodata" style="height:64px" data-day="${escapeHtml(day)}" data-val="no data"></div></div>`;
          }
          const v = Number(val);
          const h = Math.max(4, (v / 100) * 64);
          let cls = 'uptime-bar';
          if (v < 95) cls += ' uptime-bar--low';
          else if (v < 99) cls += ' uptime-bar--mid';
          return `<div class="uptime-bar-wrap"><div class="${cls}" style="height:${h}px" data-day="${escapeHtml(day)}" data-val="${v.toFixed(1)}%"></div></div>`;
        })
        .join('');

      const labels = entries
        .map(([day], idx) => {
          if (idx === 0 || idx === entries.length - 1 || idx % 7 === 0) {
            return `<span title="${escapeHtml(day)}">${escapeHtml(day.slice(-5))}</span>`;
          }
          return `<span></span>`;
        })
        .join('');

      return `
        <div class="status-monitor-item">
          <div class="status-monitor-row">
            <span class="status-monitor-name">${escapeHtml(m.name)}</span>
            ${statusBadge(m.currentStatus)}
          </div>
          <div class="mono url-ellipsis" style="font-size: 0.8125rem; color: var(--text-secondary)">${escapeHtml(m.url || '')}</div>
          <div class="uptime-bar-row">${bars || '<span style="color:var(--text-secondary)">No daily data</span>'}</div>
          <div class="uptime-bar-labels">${labels}</div>
        </div>
      `;
    });

    el.innerHTML = `
      <div class="status-preview-header">
        <div class="status-preview-title">${escapeHtml(data.projectSlug || '')}</div>
        <div class="status-preview-slug">Overall ${statusBadge(data.overallStatus)}</div>
      </div>
      <div class="status-monitor-list">${monitors.join('')}</div>
    `;

    el.querySelectorAll('.uptime-bar').forEach((bar) => {
      bar.addEventListener('mouseenter', (e) => {
        const day = bar.dataset.day || '';
        const val = bar.dataset.val || '';
        const tip = document.createElement('div');
        tip.className = 'uptime-bar-tooltip';
        tip.textContent = `${day}: ${val}`;
        bar.parentElement.appendChild(tip);
      });
      bar.addEventListener('mouseleave', () => {
        const tip = bar.parentElement.querySelector('.uptime-bar-tooltip');
        if (tip) tip.remove();
      });
    });
  }

  async function loadProjects() {
    try {
      const slugs = await api.apiGet('/api/projects');
      const uniqueSlugs = [...new Set(['default', ...(slugs || [])])];
      const targets = [
        document.getElementById('fm-project'),
        document.getElementById('status-slug-input'),
        document.getElementById('incidents-project-filter'),
      ];
      targets.forEach((sel) => {
        if (!sel) return;
        const currentVal = sel.value;
        const isFilter = sel.id === 'incidents-project-filter';
        const isMonitorForm = sel.id === 'fm-project';

        const preserved = [];
        if (isFilter) preserved.push('<option value="">All projects</option>');
        uniqueSlugs.forEach((s) => {
          preserved.push(`<option value="${escapeHtml(s)}">${escapeHtml(s)}</option>`);
        });
        if (isMonitorForm) {
          preserved.push('<option value="__new__">+ New project...</option>');
        }
        sel.innerHTML = preserved.join('');

        if (currentVal && [...sel.options].some((o) => o.value === currentVal)) {
          sel.value = currentVal;
        }
      });
    } catch (_) {}
  }

  async function loadStatusPreview() {
    const input = document.getElementById('status-slug-input');
    const slug = (input && input.value.trim()) || 'default';
    try {
      const data = await api.apiGet(`/api/status/${encodeURIComponent(slug)}`);
      renderStatusPreview(data);
    } catch (e) {
      showToast(e.message || 'Failed to load status page', true);
    }
  }

  function handleSseEvent(type, data) {
    if (type === 'open' || type === 'connected') {
      setSseStatus(true);
      return;
    }
    if (type === 'error') {
      setSseStatus(false);
      return;
    }

    if (type === 'check-result') {
      if (state.currentView === 'dashboard') loadDashboard();
      if (state.currentView === 'monitor-detail' && state.detailMonitorId && data && data.monitorId === state.detailMonitorId) {
        api
          .apiGet(`/api/monitors/${state.detailMonitorId}/checks?page=0&size=50`)
          .then((checks) => {
            charts.updateResponseTimeChart(state.responseChart, checks);
            const tbody = document.querySelector('#detail-checks-table tbody');
            if (tbody) {
              tbody.innerHTML = '';
              (checks || []).forEach((c) => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                  <td class="mono">${formatDate(c.checkedAt)}</td>
                  <td>${escapeHtml(c.nodeRegion || c.nodeId || '—')}</td>
                  <td>${statusBadge(c.status)}</td>
                  <td class="mono">${c.responseTimeMs ?? '—'}</td>
                  <td class="mono">${c.statusCode ?? '—'}</td>
                  <td>${escapeHtml(c.error || '—')}</td>
                `;
                tbody.appendChild(tr);
              });
            }
          })
          .catch(() => {});
      }
      return;
    }

    if (type === 'monitor-created' || type === 'monitor-deleted') {
      if (state.currentView === 'dashboard') loadDashboard();
      if (state.currentView === 'monitors') loadMonitorsView();
      const deletedId = data != null ? Number(data) : null;
      if (type === 'monitor-deleted' && deletedId === state.detailMonitorId) {
        setHashForView('monitors');
        showView('monitors');
      }
      return;
    }

    if (type === 'monitor-updated') {
      if (state.currentView === 'dashboard') loadDashboard();
      if (state.currentView === 'monitors') loadMonitorsView();
      if (
        state.currentView === 'monitor-detail' &&
        state.detailMonitorId &&
        data &&
        data.id === state.detailMonitorId
      ) {
        loadMonitorDetail(state.detailMonitorId);
      }
      return;
    }

    if (type === 'node-registered' || type === 'node-removed') {
      if (state.currentView === 'cluster') loadCluster();
      if (state.currentView === 'dashboard') loadDashboard();
    }
  }

  function initNavigation() {
    document.querySelectorAll('.nav-item').forEach((btn) => {
      btn.addEventListener('click', () => {
        const nav = btn.getAttribute('data-nav');
        const map = {
          dashboard: 'dashboard',
          monitors: 'monitors',
          cluster: 'cluster',
          incidents: 'incidents',
          status: 'status',
        };
        const v = map[nav];
        if (v) {
          setHashForView(v);
          showView(v);
        }
      });
    });

    document.getElementById('btn-back-monitors').addEventListener('click', () => {
      setHashForView('monitors');
      showView('monitors');
    });

    document.getElementById('btn-open-add-monitor').addEventListener('click', () => {
      loadProjects();
      document.getElementById('modal-add-monitor').classList.add('open');
    });

    document.getElementById('btn-cancel-add-monitor').addEventListener('click', () => {
      document.getElementById('modal-add-monitor').classList.remove('open');
    });

    document.getElementById('modal-add-monitor').addEventListener('click', (e) => {
      if (e.target.id === 'modal-add-monitor') {
        document.getElementById('modal-add-monitor').classList.remove('open');
      }
    });

    document.getElementById('form-add-monitor').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      let projectSlug = fd.get('projectSlug') || 'default';
      if (projectSlug === '__new__') {
        const custom = (document.getElementById('fm-project-custom').value || '').trim();
        projectSlug = custom || 'default';
      }
      const body = {
        name: fd.get('name'),
        url: fd.get('url'),
        type: fd.get('type'),
        intervalSeconds: Number(fd.get('intervalSeconds')) || 60,
        timeoutMs: Number(fd.get('timeoutMs')) || 10000,
        expectedStatusCode: Number(fd.get('expectedStatusCode')) || 200,
        keyword: fd.get('keyword') || null,
        projectSlug: projectSlug,
      };
      try {
        await api.apiPost('/api/monitors', body);
        document.getElementById('modal-add-monitor').classList.remove('open');
        e.target.reset();
        if (fmProjectCustomGroup) fmProjectCustomGroup.style.display = 'none';
        loadProjects();
        showToast('Monitor created');
        loadMonitorsView();
        if (state.currentView === 'dashboard') loadDashboard();
      } catch (err) {
        showToast(err.message || 'Create failed', true);
      }
    });

    document.getElementById('btn-toggle-pause').addEventListener('click', async () => {
      if (state.detailMonitorId == null) return;
      try {
        await api.apiPost(`/api/monitors/${state.detailMonitorId}/pause`, {});
        showToast('Monitor updated');
        await loadMonitorDetail(state.detailMonitorId);
        loadDashboard();
      } catch (err) {
        showToast(err.message || 'Update failed', true);
      }
    });

    document.getElementById('btn-delete-monitor').addEventListener('click', async () => {
      if (state.detailMonitorId == null) return;
      if (!confirm('Delete this monitor?')) return;
      try {
        await api.apiDelete(`/api/monitors/${state.detailMonitorId}`);
        showToast('Monitor deleted');
        setHashForView('monitors');
        showView('monitors');
      } catch (err) {
        showToast(err.message || 'Delete failed', true);
      }
    });

    document.getElementById('btn-load-status').addEventListener('click', () => {
      loadStatusPreview();
    });

    // Project slug dropdown: show/hide custom input
    const fmProjectSelect = document.getElementById('fm-project');
    const fmProjectCustomGroup = document.getElementById('fm-project-custom-group');
    if (fmProjectSelect && fmProjectCustomGroup) {
      function syncCustomProjectVisibility() {
        fmProjectCustomGroup.style.display = fmProjectSelect.value === '__new__' ? '' : 'none';
      }
      fmProjectSelect.addEventListener('change', syncCustomProjectVisibility);
      new MutationObserver(syncCustomProjectVisibility).observe(fmProjectSelect, { childList: true });
    }

    // Add Node modal
    function updateNodeCmd() {
      const region = (document.getElementById('node-region').value || 'US-East').trim();
      const port = (document.getElementById('node-port').value || '8084').trim();
      const cmd = `java -jar truesignal-monitor/target/truesignal-monitor-1.0-SNAPSHOT.jar --server.port=${port} --truesignal.monitor.region=${region}`;
      const el = document.getElementById('node-cmd-text');
      if (el) el.textContent = cmd;
    }

    document.getElementById('btn-open-add-node').addEventListener('click', () => {
      updateNodeCmd();
      document.getElementById('modal-add-node').classList.add('open');
    });

    document.getElementById('btn-cancel-add-node').addEventListener('click', () => {
      document.getElementById('modal-add-node').classList.remove('open');
    });

    document.getElementById('modal-add-node').addEventListener('click', (e) => {
      if (e.target.id === 'modal-add-node') {
        document.getElementById('modal-add-node').classList.remove('open');
      }
    });

    document.getElementById('node-region').addEventListener('input', updateNodeCmd);
    document.getElementById('node-port').addEventListener('input', updateNodeCmd);

    document.getElementById('btn-copy-node-cmd').addEventListener('click', () => {
      const text = document.getElementById('node-cmd-text').textContent;
      navigator.clipboard.writeText(text).then(() => {
        showToast('Command copied to clipboard');
      }).catch(() => {
        showToast('Failed to copy', true);
      });
    });

    // Incidents project filter
    const incidentsFilter = document.getElementById('incidents-project-filter');
    if (incidentsFilter) {
      incidentsFilter.addEventListener('change', () => {
        if (state.currentView === 'incidents') loadIncidents();
      });
    }

    window.addEventListener('hashchange', () => {
      const parsed = parseHash();
      if (parsed && parsed.view === 'monitor-detail') {
        showView('monitor-detail', { monitorId: parsed.id });
      } else {
        const h = window.location.hash.replace(/^#\/?/, '');
        if (h === 'monitors' || h === 'cluster' || h === 'incidents' || h === 'status') {
          showView(h);
        } else if (!h || h === 'dashboard') {
          showView('dashboard');
        }
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    setSseStatus('reconnecting');
    loadProjects();
    const parsed = parseHash();
    if (parsed && parsed.view === 'monitor-detail') {
      showView('monitor-detail', { monitorId: parsed.id });
    } else {
      const h = window.location.hash.replace(/^#\/?/, '');
      if (h === 'monitors' || h === 'cluster' || h === 'incidents' || h === 'status') {
        showView(h);
      } else {
        showView('dashboard');
      }
    }

    try {
      state.sse = api.connectSSE(handleSseEvent);
    } catch (e) {
      setSseStatus(false);
      showToast('Could not connect to live stream', true);
    }
  });
})();
