const sessionId = crypto.randomUUID();
const messagesEl = document.getElementById('messages');
const inputEl = document.getElementById('input');
const sendEl = document.getElementById('send');

function addBubble(text, role) {
    const div = document.createElement('div');
    div.className = `bubble ${role}`;
    div.textContent = text;
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

async function sendMessage() {
    const text = inputEl.value.trim();
    if (!text) return;

    addBubble(text, 'user');
    inputEl.value = '';
    sendEl.disabled = true;
    inputEl.disabled = true;

    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId, message: text })
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        if (data.action === 'cleared') {
            messagesEl.replaceChildren();
            return;
        }
        addBubble(data.message, 'assistant');
    } catch {
        addBubble('Something went wrong. Is LMStudio running?', 'error');
    } finally {
        sendEl.disabled = false;
        inputEl.disabled = false;
        inputEl.focus();
    }
}

sendEl.addEventListener('click', sendMessage);
inputEl.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

const ERROR_POLL_MS = 5000;
let errorPolling = false;

async function pollErrors() {
    if (errorPolling) return;
    errorPolling = true;
    try {
        const response = await fetch('/errors');
        if (!response.ok) return;
        const errors = await response.json();
        for (const e of errors) {
            addBubble('⚠️ ' + e.toolName + ': ' + e.message, 'error');
        }
    } catch {
        // swallow — backend may be temporarily unavailable
    } finally {
        errorPolling = false;
    }
}

setInterval(pollErrors, ERROR_POLL_MS);
