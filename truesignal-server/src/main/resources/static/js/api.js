(function () {
  const API_BASE = '';

  async function apiGet(path) {
    const r = await fetch(API_BASE + path);
    const text = await r.text();
    if (!r.ok) {
      let msg = r.statusText;
      try {
        const j = JSON.parse(text);
        msg = j.message || j.error || text || msg;
      } catch (_) {
        if (text) msg = text;
      }
      throw new Error(msg);
    }
    if (!text) return null;
    return JSON.parse(text);
  }

  async function apiPost(path, body) {
    const r = await fetch(API_BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const text = await r.text();
    if (!r.ok) {
      let msg = r.statusText;
      try {
        const j = JSON.parse(text);
        msg = j.message || j.error || text || msg;
      } catch (_) {
        if (text) msg = text;
      }
      throw new Error(msg);
    }
    if (!text) return null;
    return JSON.parse(text);
  }

  async function apiDelete(path) {
    const r = await fetch(API_BASE + path, { method: 'DELETE' });
    if (!r.ok) {
      const text = await r.text();
      throw new Error(text || r.statusText);
    }
    return r;
  }

  function connectSSE(onEvent) {
    const es = new EventSource(API_BASE + '/stream');
    const types = [
      'connected',
      'check-result',
      'monitor-created',
      'monitor-updated',
      'monitor-deleted',
      'node-registered',
      'node-removed',
    ];
    types.forEach((type) => {
      es.addEventListener(type, (e) => {
        let data = e.data;
        if (data != null && data !== '') {
          try {
            data = JSON.parse(data);
          } catch (_) {}
        }
        onEvent(type, data);
      });
    });
    es.onopen = () => {
      onEvent('open', null);
    };
    es.onerror = () => {
      onEvent('error', null);
    };
    return es;
  }

  window.TrueSignalAPI = {
    apiGet,
    apiPost,
    apiDelete,
    connectSSE,
  };
})();
