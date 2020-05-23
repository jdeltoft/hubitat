definition(
    name: "Open Door Alerts",
    namespace: "hubitat",
    author: "Justin Eltoft",
    description: "Alert if a set of Doors/Contacts is open for N minutes (with repeat)",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}


// TODO:
// - add something to "snooze" the alarm until next sensor change
// - add ability to set restrictions on when the app runs (dates, times, etc)
// - test with multiple pushes, wasn't sure if that needed to change to a loop for notifications
// - find a way to clean up the virtual device if uninstalled


def mainPage() {
    def appVersion "0.10"
	dynamicPage(name: "mainPage", title: "Open Door Alert (v$appVersion)", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Open Door Alert (NameMe)"
			if(thisName) app.updateLabel("$thisName")
            input "contactSensors", "capability.contactSensor", title: "Select Contact Sensors", submitOnChange: true, required: true, multiple: true
            input "alertMessage", "text", title: "Alert Message", defaultValue: "Open Door Alert!", required: true 
			input "minutesToAlert", "number", title: "Initial Alert Delay (minutes):", defaultValue: 15, submitOnChange: true
			input "repeat", "bool", title: "repeat:", defaultValue: false, submitOnChange: true
            if (repeat) {
			    input "minutesToRepeat", "number", title: "Repeat Alerts Time (minutes):", defaultValue: 30, submitOnChange: true
            }
			if(contactSensors) paragraph "Any Doors Currently Open: ${areAnyOpen()}"
			input "debugLog", "bool", title: "Enable Debug Logging:", defaultValue: false, submitOnChange: true
		}
        section("Notifications") {
            input "sendPushMessage", "capability.notification", title: "Send a push notification?", multiple: true, required: false
        }

	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def uninstalled() {
	unsubscribe()
	deleteChildDevice("OpenDoorAlert_${app.id}")
}

def initialize() {
    state.timerRunning = false
	def anyOpenDev = getChildDevice("OpenDoorAlert_${app.id}")
	if(!anyOpenDev) anyOpenDev = addChildDevice("hubitat", "Virtual Contact Sensor", "OpenDoorAlert_${app.id}", null, [label: thisName, name: thisName])

    handleDoorEvent()  // need to check for doors open at start up also

	subscribe(contactSensors, "contact", handler)
}

def areAnyOpen() {
	def anyOpen = false
	contactSensors.each {
        if(it.currentContact == "open") anyOpen = true
	}
	return anyOpen
}

def handler(evt) {
    if (debugLog) log.debug "ODA: event $evt.device $evt.value"
    handleDoorEvent()
}

def handleDoorEvent() {
	def anyOpenDev = getChildDevice("OpenDoorAlert_${app.id}")
    if(areAnyOpen() == true) {
        // show that at least one of the doors is open in the virtual device
        anyOpenDev.open()

        // if any doors open start a timer for minutesToAlert unless already running
        if (!state.timerRunning) {
            if (debugLog) log.debug "ODA: new timer for $minutesToAlert"
            state.timerRunning = true
            runIn(60*minutesToAlert, doorAlarm)  // this will replace any (stale) timer already running
        }
    } else { 
        // show that no doors are open in the virtual device
        anyOpenDev.close()

        // if no doors open, save that the timer isn't needed  (is there way to kill the timer?)
        state.timerRunning = false
    }
}

def doorAlarm() {
    if (state.timerRunning) {
        state.timerRunning = false
        send(alertMessage)

        if (repeat) {
            if (debugLog) log.debug "ODA: repeat new timer for $minutesToRepeat"
            state.timerRunning = true
            runIn(60*minutesToRepeat, doorAlarm)
        }
    }
}

private send(message) {
    if (sendPushMessage != null) {
        if (debugLog) log.debug("ODA: Send Notification: $message")
        sendPushMessage.deviceNotification(message)
    } 
}

