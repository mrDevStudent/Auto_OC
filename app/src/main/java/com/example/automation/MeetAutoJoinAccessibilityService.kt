package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MeetAutoJoinAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastJoinClickTimeMs: Long = 0L
    private var lastRetrySignalTimeMs: Long = 0L
    private var lastMediaToggleTimeMs: Long = 0L
    private var lastAccountActionTimeMs: Long = 0L

    private val delayedScanRunnable = Runnable {
        if (AutoJoinCoordinator.isSessionActive()) {
            inspectActiveWindow()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutoJoinCoordinator.isSessionActive()) return
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && pkg !in SUPPORTED_PACKAGES) return
        inspectActiveWindow()
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedScanRunnable)
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    fun requestImmediateScan() {
        mainHandler.post {
            if (AutoJoinCoordinator.isSessionActive()) {
                inspectActiveWindow()
            }
        }
    }

    private fun scheduleDelayedScan(delayMs: Long) {
        mainHandler.removeCallbacks(delayedScanRunnable)
        mainHandler.postDelayed(delayedScanRunnable, delayMs)
    }

    private fun inspectActiveWindow() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && pkg !in SUPPORTED_PACKAGES) return

        val session = AutoJoinCoordinator.session.value
        if (!session.phase.isActive()) return

        AutoJoinCoordinator.touchMeetWindowSeen()

        val allNodes = ArrayList<AccessibilityNodeInfo>(80)
        collectNodes(root, allNodes)

        val lowerLabels = allNodes.mapNotNull { node ->
            val combined = buildString {
                node.text?.let { append(it).append(' ') }
                node.contentDescription?.let { append(it) }
            }.trim().lowercase()
            combined.ifEmpty { null }
        }

        // 1. Check if already admitted and connected into the call
        if (isConnectedScreen(lowerLabels)) {
            AutoJoinCoordinator.onConnected(applicationContext)
            return
        }

        // 2. Check if waiting in the Google Meet lobby for host admission
        if (isWaitingRoomScreen(lowerLabels)) {
            AutoJoinCoordinator.onWaitingForAdmission(applicationContext)
            return
        }

        val now = System.currentTimeMillis()

        // 3. Check if admission timed out or failed -> tap Try again / Rejoin or trigger retry
        if (isErrorOrTimeoutScreen(lowerLabels)) {
            val retryButton = findClickableNodeByKeywords(allNodes, RETRY_BUTTON_KEYWORDS)
            if (retryButton != null && now - lastJoinClickTimeMs > 2_000L) {
                lastJoinClickTimeMs = now
                if (retryButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AutoJoinCoordinator.onRetryRequested(
                        applicationContext,
                        "Tapped Try again / Rejoin after timeout"
                    )
                    scheduleDelayedScan(600L)
                    return
                }
            }
            if (now - lastRetrySignalTimeMs > 4_000L) {
                lastRetrySignalTimeMs = now
                AutoJoinCoordinator.onRetryRequested(
                    applicationContext,
                    "No host response yet — retrying meeting link"
                )
            }
            return
        }

        // 4. Dismiss informational notice dialogs (e.g., "This call is being recorded")
        if (lowerLabels.any { it.contains("being recorded") || it.contains("meeting is recorded") }) {
            val ackButton = findClickableNodeByKeywords(allNodes, listOf("got it", "ok", "accept"))
            if (ackButton != null && now - lastMediaToggleTimeMs > 600L) {
                lastMediaToggleTimeMs = now
                ackButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                scheduleDelayedScan(500L)
                return
            }
        }

        // 5. Handle Gmail account selection or switching to preferredGmail
        val preferredEmail = session.preferredGmail.trim().lowercase()
        if (preferredEmail.isNotEmpty()) {
            val isAccountPicker = lowerLabels.any {
                it.contains("choose an account") ||
                    it.contains("use meet with") ||
                    it.contains("add another account") ||
                    it.contains("switch account")
            }

            if (isAccountPicker) {
                val targetEmailNode = findClickableNodeByEmail(allNodes, preferredEmail)
                if (targetEmailNode != null) {
                    if (now - lastAccountActionTimeMs > 700L) {
                        lastAccountActionTimeMs = now
                        targetEmailNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutoJoinCoordinator.onAccountSwitched(
                            applicationContext,
                            "Selected $preferredEmail"
                        )
                        scheduleDelayedScan(600L)
                    }
                    return
                }
                val continueBtn = findClickableNodeByKeywords(allNodes, listOf("continue"))
                if (continueBtn != null && now - lastAccountActionTimeMs > 700L) {
                    lastAccountActionTimeMs = now
                    continueBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    scheduleDelayedScan(600L)
                    return
                }
            } else {
                // On pre-join screen: verify current Gmail matches preferredEmail
                val visibleEmails = lowerLabels
                    .flatMap { EMAIL_REGEX.findAll(it).map { match -> match.value }.toList() }
                    .distinct()
                val matchesTarget = visibleEmails.any { it == preferredEmail }
                val hasWrongEmail = visibleEmails.isNotEmpty() && !matchesTarget

                if (hasWrongEmail && AutoJoinCoordinator.accountSwitchAttempts < 2) {
                    val switchButton = findSwitchAccountNode(allNodes, visibleEmails)
                    if (switchButton != null) {
                        if (now - lastAccountActionTimeMs > 800L) {
                            lastAccountActionTimeMs = now
                            AutoJoinCoordinator.accountSwitchAttempts++
                            switchButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutoJoinCoordinator.onAccountSwitched(
                                applicationContext,
                                "Switching Google Meet account to $preferredEmail..."
                            )
                            scheduleDelayedScan(600L)
                        }
                        return
                    }
                }
            }
        } else if (lowerLabels.any { it.contains("choose an account") || it.contains("use meet with") }) {
            val continueBtn = findClickableNodeByKeywords(allNodes, listOf("continue"))
            if (continueBtn != null && now - lastAccountActionTimeMs > 1_000L) {
                lastAccountActionTimeMs = now
                continueBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                scheduleDelayedScan(600L)
                return
            }
        }

        // 6. Auto-turn-off camera BEFORE tapping Join
        if (session.autoTurnOffCamera && AutoJoinCoordinator.camDisableAttempts < 5) {
            val camOffNode = findCameraTurnOffNode(allNodes)
            if (camOffNode != null) {
                if (now - lastMediaToggleTimeMs >= 350L) {
                    lastMediaToggleTimeMs = now
                    AutoJoinCoordinator.camDisableAttempts++
                    camOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                scheduleDelayedScan(450L)
                return
            }
        }

        // 7. Auto-mute microphone BEFORE tapping Join
        if (session.autoMuteMic && AutoJoinCoordinator.micDisableAttempts < 5) {
            val micOffNode = findMicTurnOffNode(allNodes)
            if (micOffNode != null) {
                if (now - lastMediaToggleTimeMs >= 350L) {
                    lastMediaToggleTimeMs = now
                    AutoJoinCoordinator.micDisableAttempts++
                    micOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                scheduleDelayedScan(450L)
                return
            }
        }

        // 8. Tap primary Join / Ask to join button
        val joinMatch = findJoinButton(allNodes)
        if (joinMatch != null) {
            if (now - lastJoinClickTimeMs > 1_800L) {
                val (clickableNode, label) = joinMatch
                lastJoinClickTimeMs = now
                if (clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AutoJoinCoordinator.onJoinButtonTapped(applicationContext, label)
                    scheduleDelayedScan(900L)
                }
            }
            return
        }

        // 9. Detect if Meet dropped back to home landing screen -> trigger retry
        if (isMeetHomeScreen(lowerLabels) && now - lastRetrySignalTimeMs > 4_000L) {
            lastRetrySignalTimeMs = now
            AutoJoinCoordinator.onRetryRequested(
                applicationContext,
                "Meet returned to home screen — reopening meeting code"
            )
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(node)
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, out)
        }
    }

    private fun isConnectedScreen(labels: List<String>): Boolean {
        val hasPreJoinOrWaiting = labels.any { label ->
            label == "join" ||
                label.contains("ask to join") ||
                label.contains("asking to join") ||
                label.contains("join now") ||
                label.contains("someone in the call will let you in") ||
                label.contains("ready to join")
        }
        if (hasPreJoinOrWaiting) return false

        return labels.any { label ->
            label.contains("leave call") ||
                label.contains("end call") ||
                label.contains("raise hand") ||
                label.contains("turn on captions") ||
                label.contains("turn off captions") ||
                label.contains("in-call messages") ||
                label.contains("send a reaction") ||
                label.contains("you're in the call")
        }
    }

    private fun isWaitingRoomScreen(labels: List<String>): Boolean {
        return labels.any { label ->
            label.contains("asking to join") ||
                label.contains("someone in the call will let you in") ||
                label.contains("you'll join the call when") ||
                label.contains("waiting for someone to let you in") ||
                label.contains("waiting for the host")
        }
    }

    private fun isErrorOrTimeoutScreen(labels: List<String>): Boolean {
        return labels.any { label ->
            label.contains("no one responded") ||
                label.contains("couldn't join") ||
                label.contains("you can't join") ||
                label.contains("return to home screen") ||
                label.contains("something went wrong") ||
                label.contains("meeting has ended") ||
                label.contains("you left the meeting")
        }
    }

    private fun isMeetHomeScreen(labels: List<String>): Boolean {
        return labels.any { label ->
            label == "new meeting" ||
                label.contains("join with a code") ||
                label.contains("search or enter a code")
        }
    }

    private fun findCameraTurnOffNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val combined = nodeLabel(node)
            val resId = node.viewIdResourceName?.lowercase().orEmpty()

            val excluded = combined.contains("turn on") ||
                combined.contains("is off") ||
                combined.contains("camera off") ||
                combined.contains("video off") ||
                combined.contains("switch camera") ||
                combined.contains("flip camera") ||
                combined.contains("effects") ||
                combined.contains("blur")
            if (excluded) continue

            val matchesLabel = combined.contains("turn off camera") ||
                combined.contains("turn camera off") ||
                combined.contains("turn off video") ||
                combined.contains("turn video off") ||
                combined.contains("stop video") ||
                combined.contains("camera is on") ||
                combined.contains("video is on") ||
                combined == "camera on" ||
                combined == "video on"

            val matchesToggleId = (resId.contains("video") || resId.contains("camera")) &&
                node.isCheckable && node.isChecked

            if (matchesLabel || matchesToggleId) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) {
                    return clickable
                }
            }
        }
        return null
    }

    private fun findMicTurnOffNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val combined = nodeLabel(node)
            val resId = node.viewIdResourceName?.lowercase().orEmpty()

            val excluded = combined.contains("turn on") ||
                combined.contains("unmute") ||
                combined.contains("is off") ||
                combined.contains("microphone off") ||
                combined.contains("mic off")
            if (excluded) continue

            val matchesLabel = combined.contains("turn off microphone") ||
                combined.contains("turn microphone off") ||
                combined.contains("turn off mic") ||
                combined.contains("turn mic off") ||
                combined.contains("mute microphone") ||
                combined.contains("microphone is on") ||
                combined.contains("mic is on") ||
                combined == "mute" ||
                combined == "microphone on"

            val matchesToggleId = (resId.contains("mic") || resId.contains("audio")) &&
                node.isCheckable && node.isChecked && !combined.contains("speaker")

            if (matchesLabel || matchesToggleId) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) {
                    return clickable
                }
            }
        }
        return null
    }

    private fun findClickableNodeByEmail(
        nodes: List<AccessibilityNodeInfo>,
        targetEmail: String
    ): AccessibilityNodeInfo? {
        for (node in nodes) {
            val combined = nodeLabel(node)
            if (combined.contains(targetEmail)) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) {
                    return clickable
                }
            }
        }
        return null
    }

    private fun findSwitchAccountNode(
        nodes: List<AccessibilityNodeInfo>,
        visibleWrongEmails: List<String>
    ): AccessibilityNodeInfo? {
        // First try an explicit "Switch" button (excluding "Switch here" / "Switch camera")
        for (node in nodes) {
            val combined = nodeLabel(node)
            if (combined == "switch" || combined.contains("switch account")) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) return clickable
            }
        }
        // Fallback: tap the signed-in account pill/row showing the wrong email
        for (node in nodes) {
            val combined = nodeLabel(node)
            if (visibleWrongEmails.any { combined.contains(it) } ||
                combined.contains("signed in as") ||
                combined.contains("joining as")
            ) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) return clickable
            }
        }
        return null
    }

    private fun findJoinButton(nodes: List<AccessibilityNodeInfo>): Pair<AccessibilityNodeInfo, String>? {
        for (keyword in JOIN_BUTTON_KEYWORDS) {
            for (node in nodes) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val candidate = if (text.isNotEmpty()) text else desc
                if (candidate.isEmpty()) continue
                val lower = candidate.lowercase()

                if (lower.contains("companion") ||
                    lower.contains("other ways") ||
                    lower.contains("phone for audio") ||
                    lower.contains("presenter") ||
                    lower.contains("asking to join") ||
                    lower.contains("ready to join")
                ) {
                    continue
                }

                val matches = if (keyword == "join") {
                    lower == "join" || lower == "join meeting" || lower == "join call"
                } else {
                    lower.contains(keyword)
                }

                if (matches) {
                    val clickable = findClickableAncestor(node)
                    if (clickable != null && clickable.isEnabled) {
                        return clickable to candidate
                    }
                }
            }
        }
        return null
    }

    private fun findClickableNodeByKeywords(
        nodes: List<AccessibilityNodeInfo>,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        for (node in nodes) {
            val combined = nodeLabel(node)
            if (combined.isEmpty()) continue
            if (keywords.any { combined.contains(it) }) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) {
                    return clickable
                }
            }
        }
        return null
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return buildString {
            node.text?.let { append(it).append(' ') }
            node.contentDescription?.let { append(it) }
        }.trim().lowercase()
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    companion object {
        @Volatile
        var activeInstance: MeetAutoJoinAccessibilityService? = null
            private set

        private val EMAIL_REGEX = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")

        private val SUPPORTED_PACKAGES = setOf(
            "com.google.android.apps.meetings",
            "com.google.android.gm",
            "com.android.chrome"
        )

        private val JOIN_BUTTON_KEYWORDS = listOf(
            "ask to join",
            "join now",
            "switch here",
            "rejoin",
            "join"
        )

        private val RETRY_BUTTON_KEYWORDS = listOf(
            "try again",
            "rejoin",
            "ask to join"
        )
    }
}
