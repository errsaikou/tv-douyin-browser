/**
 * TV Douyin Browser - Anti-Stutter Injection Script
 *
 * Problem: Skyworth (创维) TV hardware decoders have bugs with H.265/HEVC
 * and AV1 codecs, causing severe stuttering during 4K video playback.
 *
 * Solution: Monkey-patch browser codec detection APIs to report that
 * H.265 and AV1 are NOT supported. This forces Douyin's player to
 * fall back to H.264/AVC, which plays smoothly on all devices.
 *
 * This script is injected into every page load via WebView.evaluateJavascript().
 */
(function() {
    'use strict';

    // =============================================
    // Patch 1: MediaSource.isTypeSupported()
    // This is the primary API that Douyin's player
    // uses to detect codec capabilities.
    // =============================================
    if (typeof MediaSource !== 'undefined' && MediaSource.isTypeSupported) {
        var originalIsTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);

        MediaSource.isTypeSupported = function(type) {
            if (type && typeof type === 'string') {
                var lowerType = type.toLowerCase();

                // Block HEVC / H.265 (codec identifiers: hev1, hvc1)
                if (lowerType.indexOf('hev1') !== -1 || lowerType.indexOf('hvc1') !== -1) {
                    console.log('[TVBrowser] Blocked HEVC:', type);
                    return false;
                }

                // Block AV1 (codec identifier: av01)
                if (lowerType.indexOf('av01') !== -1) {
                    console.log('[TVBrowser] Blocked AV1:', type);
                    return false;
                }
            }
            return originalIsTypeSupported(type);
        };

        console.log('[TVBrowser] MediaSource.isTypeSupported patched');
    }

    // =============================================
    // Patch 2: HTMLMediaElement.canPlayType()
    // Secondary API that some players also check.
    // =============================================
    if (typeof HTMLMediaElement !== 'undefined' && HTMLMediaElement.prototype.canPlayType) {
        var originalCanPlayType = HTMLMediaElement.prototype.canPlayType;

        HTMLMediaElement.prototype.canPlayType = function(type) {
            if (type && typeof type === 'string') {
                var lowerType = type.toLowerCase();
                if (lowerType.indexOf('hev1') !== -1 ||
                    lowerType.indexOf('hvc1') !== -1 ||
                    lowerType.indexOf('av01') !== -1) {
                    console.log('[TVBrowser] canPlayType blocked:', type);
                    return '';  // Empty string = "not supported"
                }
            }
            return originalCanPlayType.call(this, type);
        };

        console.log('[TVBrowser] HTMLMediaElement.canPlayType patched');
    }

    // =============================================
    // Patch 3: Force all links to open in current window
    // TV browsers don't support multi-tabs, so we remove target="_blank"
    // =============================================
    document.addEventListener('click', function(e) {
        var target = e.target;
        while (target && target.tagName !== 'A') {
            target = target.parentNode;
        }
        if (target && target.tagName === 'A') {
            if (target.getAttribute('target') === '_blank') {
                target.setAttribute('target', '_self');
            }
        }
    }, true);

    console.log('[TVBrowser] Codec filter active — HEVC/AV1 blocked, using H.264');
})();
