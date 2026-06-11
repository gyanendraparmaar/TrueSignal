(function () {
  function createResponseTimeChart(canvasId, checkResults) {
    const el = document.getElementById(canvasId);
    if (!el) return null;
    const sorted = [...(checkResults || [])].sort(
      (a, b) => new Date(a.checkedAt) - new Date(b.checkedAt)
    );
    const labels = sorted.map((c) => {
      const d = new Date(c.checkedAt);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    });
    const data = sorted.map((c) => (c.responseTimeMs != null ? Number(c.responseTimeMs) : 0));
    return new Chart(el.getContext('2d'), {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Response time (ms)',
            data,
            borderColor: '#6c5ce7',
            backgroundColor: 'rgba(108, 92, 231, 0.12)',
            fill: true,
            tension: 0.35,
            pointRadius: 3,
            pointHoverRadius: 5,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { intersect: false, mode: 'index' },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label(ctx) {
                const v = ctx.parsed.y;
                return v != null ? `${v} ms` : '';
              },
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { maxRotation: 0, color: '#636e72', font: { size: 11 } },
          },
          y: {
            beginAtZero: true,
            grid: { color: 'rgba(0,0,0,0.06)' },
            ticks: { color: '#636e72', font: { size: 11 } },
          },
        },
      },
    });
  }

  function updateResponseTimeChart(chart, checkResults) {
    if (!chart) return;
    const sorted = [...(checkResults || [])].sort(
      (a, b) => new Date(a.checkedAt) - new Date(b.checkedAt)
    );
    chart.data.labels = sorted.map((c) => {
      const d = new Date(c.checkedAt);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    });
    chart.data.datasets[0].data = sorted.map((c) =>
      c.responseTimeMs != null ? Number(c.responseTimeMs) : 0
    );
    chart.update();
  }

  function createUptimeGauge(canvasId, percent) {
    const el = document.getElementById(canvasId);
    if (!el) return null;
    const p = Math.min(100, Math.max(0, Number(percent) || 0));
    const rest = 100 - p;
    let main = '#e17055';
    if (p > 99) main = '#00b894';
    else if (p > 95) main = '#fdcb6e';
    const labelEl = document.getElementById(canvasId + '-label');
    if (labelEl) {
      labelEl.textContent = `${p.toFixed(1)}%`;
    }
    return new Chart(el.getContext('2d'), {
      type: 'doughnut',
      data: {
        labels: ['uptime', 'rest'],
        datasets: [
          {
            data: [p, rest],
            backgroundColor: [main, '#e9ecef'],
            borderWidth: 0,
            hoverOffset: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false },
        },
      },
    });
  }

  window.TrueSignalCharts = {
    createResponseTimeChart,
    updateResponseTimeChart,
    createUptimeGauge,
  };
})();
