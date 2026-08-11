/**
 * TV Douyin Browser — Phone Remote Control Logic
 *
 * Handles:
 * 1. WebSocket connection to TV (auto-reconnect)
 * 2. Touchpad gesture recognition (drag → cursor, tap → click, two-finger → scroll)
 * 3. Text input submission
 * 4. Quick action button dispatch
 */
(function() {
    'use strict';

    // WebSocket address — {{WS_HOST}} is replaced by the server with actual IP:port
    var WS_URL = 'ws://{{WS_HOST}}';
    var ws = null;
    var isConnected = false;
    var reconnectTimer = null;

    // DOM elements
    var statusDot = document.getElementById('statusDot');
    var statusText = document.getElementById('statusText');
    var touchpad = document.getElementById('touchpad');
    var searchInput = document.getElementById('searchInput');

    // =========================================================
    // WebSocket Connection (auto-reconnect on disconnect)
    // =========================================================

    function connect() {
        if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) {
            return;
        }

        try {
            ws = new WebSocket(WS_URL);
        } catch (e) {
            console.error('WebSocket creation failed:', e);
            scheduleReconnect();
            return;
        }

        ws.onopen = function() {
            isConnected = true;
            statusDot.className = 'connected';
            statusText.textContent = '已连接到电视 ✓';
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        };

        ws.onclose = function() {
            isConnected = false;
            statusDot.className = '';
            statusText.textContent = '连接断开，正在重连...';
            scheduleReconnect();
        };

        ws.onerror = function() {
            isConnected = false;
            statusDot.className = '';
            statusText.textContent = '连接出错，正在重连...';
        };

        ws.onmessage = function(event) {
            // Handle messages from TV (e.g., confirmation)
            try {
                var data = JSON.parse(event.data);
                if (data.type === 'connected') {
                    statusText.textContent = data.message || '已连接到电视 ✓';
                }
            } catch (e) {}
        };
    }

    function scheduleReconnect() {
        if (reconnectTimer) return;
        reconnectTimer = setTimeout(function() {
            reconnectTimer = null;
            connect();
        }, 2000);
    }

    function send(data) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(data));
        }
    }

    // =========================================================
    // Touchpad: Single-Finger Drag → Cursor Move / Tap → Click
    // =========================================================

    var touchState = {
        tracking: false,
        lastX: 0,
        lastY: 0,
        startX: 0,
        startY: 0,
        startTime: 0,
        moved: false,
        fingerCount: 0
    };

    var TAP_THRESHOLD = 12;   // Max movement (px) to still count as a tap
    var TAP_TIME_LIMIT = 300; // Max duration (ms) to count as a tap

    touchpad.addEventListener('touchstart', function(e) {
        e.preventDefault();
        touchState.fingerCount = e.touches.length;

        if (e.touches.length === 1) {
            var touch = e.touches[0];
            touchState.tracking = true;
            touchState.lastX = touch.clientX;
            touchState.lastY = touch.clientY;
            touchState.startX = touch.clientX;
            touchState.startY = touch.clientY;
            touchState.startTime = Date.now();
            touchState.moved = false;
        }
    }, { passive: false });

    touchpad.addEventListener('touchmove', function(e) {
        e.preventDefault();

        // Two-finger scroll
        if (e.touches.length === 2) {
            handleTwoFingerScroll(e);
            return;
        }

        if (!touchState.tracking || e.touches.length !== 1) return;

        var touch = e.touches[0];
        var dx = touch.clientX - touchState.lastX;
        var dy = touch.clientY - touchState.lastY;

        touchState.lastX = touch.clientX;
        touchState.lastY = touch.clientY;

        // Check if finger moved enough to count as drag (not tap)
        var totalDx = Math.abs(touch.clientX - touchState.startX);
        var totalDy = Math.abs(touch.clientY - touchState.startY);
        if (totalDx > TAP_THRESHOLD || totalDy > TAP_THRESHOLD) {
            touchState.moved = true;
        }

        // Send cursor movement to TV
        if (touchState.moved) {
            send({ type: 'move', dx: dx, dy: dy });
        }
    }, { passive: false });

    touchpad.addEventListener('touchend', function(e) {
        e.preventDefault();

        if (!touchState.tracking) return;

        // Only handle tap if we had a single finger
        if (touchState.fingerCount === 1) {
            var elapsed = Date.now() - touchState.startTime;

            // Detect tap: short touch + no significant movement
            if (!touchState.moved && elapsed < TAP_TIME_LIMIT) {
                send({ type: 'click' });

                // Visual ripple
                showRipple(touchState.startX, touchState.startY);

                // Haptic feedback
                if (navigator.vibrate) {
                    navigator.vibrate(15);
                }
            }
        }

        touchState.tracking = false;
        touchState.fingerCount = e.touches.length;
    }, { passive: false });

    touchpad.addEventListener('touchcancel', function() {
        touchState.tracking = false;
    });

    // =========================================================
    // Touchpad: Two-Finger Scroll
    // =========================================================

    var scrollState = {
        tracking: false,
        lastY: 0
    };

    function handleTwoFingerScroll(e) {
        var midY = (e.touches[0].clientY + e.touches[1].clientY) / 2;

        if (!scrollState.tracking) {
            scrollState.tracking = true;
            scrollState.lastY = midY;
            return;
        }

        var dy = midY - scrollState.lastY;
        scrollState.lastY = midY;

        // Send scroll (inverted: finger up = scroll down, amplified 3x)
        send({ type: 'scroll', dy: -dy * 3 });
    }

    // Reset scroll state when fewer than 2 fingers
    touchpad.addEventListener('touchend', function(e) {
        if (e.touches.length < 2) {
            scrollState.tracking = false;
        }
    });

    // =========================================================
    // Tap Ripple Effect
    // =========================================================

    function showRipple(x, y) {
        var rect = touchpad.getBoundingClientRect();
        var ripple = document.createElement('div');
        ripple.className = 'ripple';
        ripple.style.left = (x - rect.left - 25) + 'px';
        ripple.style.top = (y - rect.top - 25) + 'px';
        touchpad.appendChild(ripple);
        setTimeout(function() {
            ripple.remove();
        }, 500);
    }

    // =========================================================
    // Text Input → TV Search
    // =========================================================

    window.sendSearch = function() {
        var text = searchInput.value.trim();
        if (!text) return;

        // If user entered a URL or domain name (e.g. "v.qq.com" or "https://..."), navigate to it
        if (text.indexOf('http://') === 0 || text.indexOf('https://') === 0 || (text.indexOf('.') !== -1 && text.indexOf(' ') === -1)) {
            send({ type: 'navigate', url: text });
        } else {
            // Otherwise, send as input text to current page search box
            send({ type: 'input', text: text });
        }

        searchInput.value = '';
        searchInput.blur(); // Hide keyboard

        // Button feedback
        var btn = document.getElementById('sendBtn');
        var originalText = btn.textContent;
        btn.textContent = '已发送 ✓';
        btn.style.background = '#25d366';
        setTimeout(function() {
            btn.textContent = originalText;
            btn.style.background = '';
        }, 1500);
    };

    // Enter key = send
    searchInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            window.sendSearch();
        }
    });

    // =========================================================
    // Quick Action Buttons
    // =========================================================

    window.sendAction = function(action) {
        send({ type: 'action', action: action });

        // Haptic feedback
        if (navigator.vibrate) {
            navigator.vibrate(20);
        }
    };

    // =========================================================
    // Initialize
    // =========================================================

    connect();

})();
