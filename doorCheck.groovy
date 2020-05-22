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

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this Door Check", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
            input "contactSensors", "capability.contactSensor", title: "Select Contact Sensors", submitOnChange: true, required: true, multiple: true
			input "minutesToAlert", "number", title: "Number of Minutes Left Open to Notify:", defaultValue: 15, submitOnChange: true
			input "repeat", "boolean", title: "repeat:", defaultValue: false, submitOnChange: true
			if(contactSensors) paragraph "Current state of doors: ${areAnyOpen()}"
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

def initialize() {
    state.timerRunning = false
	def anyOpenDev = getChildDevice("DoorCheck_${app.id}")
	if(!anyOpenDev) anyOpenDev = addChildDevice("hubitat", "Virtual Contact Sensor", "DoorCheck_${app.id}", null, [label: thisName, name: thisName])

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
    log.debug "Door Check: event $evt.device $evt.value"
    handleDoorEvent()
}

def handleDoorEvent() {
	def anyOpenDev = getChildDevice("DoorCheck_${app.id}")
    if(areAnyOpen() == true) {
        // show that at least one of the doors is open in the virtual device
        anyOpenDev.open()

        // if any doors open start a timer for minutesToAlert unless already running
        if (!state.timerRunning) {
            log.debug "Door Check: new timer for $minutesToAlert"
            state.timerRunning = true
            runIn(60*minutesToAlert, doorAlarm)
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
        if (sendPushMessage != null) {
            log.debug "Door Check: send push msg..."
        }

        if (repeat) {
            log.debug "Door Check: repeat new timer for $minutesToAlert"
            state.timerRunning = true
            runIn(60*minutesToAlert, doorAlarm)
        }
    }
}


