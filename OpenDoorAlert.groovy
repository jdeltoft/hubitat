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
	dynamicPage(name: "mainPage", title: "Open Door Alert (ver:0.11)", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Open Door Alert (NameMe)"
			if(thisName) app.updateLabel("$thisName")
            input "contactSensors", "capability.contactSensor", title: "Select Contact Sensors", submitOnChange: true, required: true, multiple: true
            input "alertMessage", "text", title: "Alert Message", defaultValue: "Open Door Alert!", required: true 
			input "minutesToAlertDay", "number", title: "Initial Day Alert Delay (minutes):", defaultValue: 15, submitOnChange: true
			input "minutesToAlertNight", "number", title: "Initial Night Alert Delay (minutes):", defaultValue: 5, submitOnChange: true
			input "repeat", "bool", title: "repeat:", defaultValue: false, submitOnChange: true
            if (repeat) {
			    input "minutesToRepeat", "number", title: "Repeat Alerts Time (minutes):", defaultValue: 30, submitOnChange: true
            }
			if(contactSensors) paragraph "Any Doors Currently Open: ${areAnyOpen()}"
		}
        section("Notifications") {
            input "sendPushMessage", "capability.notification", title: "Send a push notification?", multiple: true, required: false
        }
        section("Day to Night") {
			//input "sunsetOffset", "number", title: "Sunset Offset (minutes +/-):", defaultValue: 0, submitOnChange: true
            input "sunsetOffset", "text", title: "Sunset Offset (+/-) HH:MM", required: false
        }
        section("Debug") {
			input "debugLog", "bool", title: "Enable Debug Logging:", defaultValue: false, submitOnChange: true
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

        // if any doors open start a timer for minutesToAlertDay unless already running
        if (!state.timerRunning) {
            def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
            Date latestdate = new Date();

            def min = minutesToAlertDay

            if (latestdate.after(astroInfo.sunset)) {
                if (debugLog) log.debug "ODA: using night alert"
                min = minutesToAlertNight
            } else {
                if (debugLog) log.debug "ODA: using day alert"
            }

            if (debugLog) log.debug "ODA: new timer for $min"
            state.timerRunning = true
            runIn(60*minutesToAlertDay, doorAlarm)  // this will replace any (stale) timer already running
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

