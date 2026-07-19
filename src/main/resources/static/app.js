let endpointId = null;
let pollInterval = null;

const API_BASE = '/api/v1';
const DEMO_BASE = '/demo';

// Elements
const btnSetup = document.getElementById('btn-setup');
const btnPublish = document.getElementById('btn-publish');
const btnClear = document.getElementById('btn-clear');
const toggleFailure = document.getElementById('toggle-failure');
const setupStatus = document.getElementById('setup-status');
const failureStatusText = document.getElementById('failure-status-text');
const terminal = document.getElementById('terminal');
const inputEndpointUrl = document.getElementById('endpoint-url');

// Format current time
const now = () => new Date().toLocaleTimeString('en-US', { hour12: false, fractionalSecondDigits: 3 });

// Terminal Logging
function logTerminal(htmlContent) {
    const div = document.createElement('div');
    div.className = 'terminal-line';
    div.innerHTML = `<span style="color:#475569">[${now()}]</span> ${htmlContent}`;
    terminal.appendChild(div);
    terminal.scrollTop = terminal.scrollHeight;
}

// 1. Setup Demo Endpoint
btnSetup.addEventListener('click', async () => {
    try {
        btnSetup.disabled = true;
        setupStatus.textContent = "Registering endpoint...";
        
        // Register Endpoint (point it to our local /demo/receive)
        const hostUrl = window.location.origin;
        let targetUrl = inputEndpointUrl.value.trim();
        if (!targetUrl) {
            targetUrl = `${hostUrl}/demo/receive`;
        }

        const resEndpoint = await fetch(`${API_BASE}/endpoints`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: targetUrl,
                secretKey: "demo-secret-key-123"
            })
        });
        
        if (!resEndpoint.ok) throw new Error("Failed to register endpoint");
        const endpointData = await resEndpoint.json();
        endpointId = endpointData.id;

        // Subscribe to event
        const resSub = await fetch(`${API_BASE}/endpoints/subscribe`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                endpointId: endpointId,
                eventType: "user.created" 
            })
        });
        
        if (!resSub.ok) throw new Error("Failed to subscribe");

        setupStatus.textContent = `✅ Success! Endpoint ID: ${endpointId}`;
        btnSetup.style.display = 'none';
        btnPublish.disabled = false;
        
        logTerminal(`<span class="info">System ready. Mock Client registered to receive 'user.created' events.</span>`);
        
        // Start polling logs
        startPolling();
        
    } catch (err) {
        setupStatus.textContent = `❌ Error: ${err.message}`;
        btnSetup.disabled = false;
    }
});

// 2. Toggle Failure
toggleFailure.addEventListener('change', async (e) => {
    const isFailing = e.target.checked;
    try {
        await fetch(`${DEMO_BASE}/settings/failure?fail=${isFailing}`, { method: 'POST' });
        failureStatusText.textContent = isFailing ? "Server is OFFLINE (HTTP 500)" : "Server is ONLINE (HTTP 200)";
        failureStatusText.style.color = isFailing ? "var(--danger)" : "var(--text-secondary)";
        logTerminal(`<span class="system">Mock Client status changed to: ${isFailing ? 'HTTP 500 Internal Server Error' : 'HTTP 200 OK'}</span>`);
    } catch (err) {
        console.error("Failed to set failure state", err);
    }
});

// 3. Publish Event
btnPublish.addEventListener('click', async () => {
    try {
        btnPublish.disabled = true;
        const idempotencyKey = "demo-" + Date.now();
        
        logTerminal(`<span class="system">Publishing event (Idempotency Key: ${idempotencyKey})...</span>`);
        
        const res = await fetch(`${API_BASE}/events`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                eventType: "user.created",
                idempotencyKey: idempotencyKey,
                payload: {
                    userId: "USR-999",
                    email: "demo@example.com",
                    status: "active"
                }
            })
        });
        
        if (res.ok) {
            logTerminal(`<span class="info">Event published successfully. RabbitMQ worker will pick it up shortly.</span>`);
        } else {
            throw new Error("Failed to publish");
        }
    } catch (err) {
        logTerminal(`<span class="error">Error publishing event: ${err.message}</span>`);
    } finally {
        setTimeout(() => { btnPublish.disabled = false; }, 1000);
    }
});

// 4. Poll Logs
let lastLogCount = 0;
function startPolling() {
    if (pollInterval) clearInterval(pollInterval);
    pollInterval = setInterval(async () => {
        try {
            const res = await fetch(`${DEMO_BASE}/logs`);
            const logs = await res.json();
            
            if (logs.length > lastLogCount) {
                // We have new logs
                const newLogs = logs.slice(0, logs.length - lastLogCount).reverse(); // latest are at the front of array but we want chronological
                
                newLogs.forEach(log => {
                    const html = `
                        <div class="incoming">
                            <span class="req-method">POST</span> <span class="req-path">/demo/receive</span>
                            <div class="req-headers">
                                x-webhook-event-id: ${log.headers['x-webhook-event-id']}<br>
                                x-webhook-signature: ${log.headers['x-webhook-signature']}
                            </div>
                            <div class="req-body">${JSON.stringify(JSON.parse(log.payload), null, 2)}</div>
                        </div>
                    `;
                    logTerminal(html);
                });
                lastLogCount = logs.length;
            }
        } catch (err) {
            // Ignore polling errors
        }
    }, 1000);
}

// 5. Clear Logs
btnClear.addEventListener('click', async () => {
    await fetch(`${DEMO_BASE}/logs`, { method: 'DELETE' });
    terminal.innerHTML = '<div class="terminal-line system">Waiting for incoming webhooks on POST /demo/receive...</div>';
    lastLogCount = 0;
});
