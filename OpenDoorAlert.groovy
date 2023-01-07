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
  dynamicPage(name: "mainPage", title: "Open Door Alert (ver:0.15)", install: true, uninstall: true) {
    section {
      input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Open Door Alert (NameMe)"
      if(thisName) app.updateLabel("$thisName")

      input "contactSensors", "capability.contactSensor", title: "Select Contact Sensors", submitOnChange: true, required: true, multiple: true
      input "alarmLights", "capability.switch", title: "Select Alarm Lights", submitOnChange: true, required: false, multiple: true
      input "snoozeCount", "capability.temperatureMeasurement", title: "Select Snooze Value", submitOnChange: true, required: false, multiple: false
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
  state.doorAlarmTime = 0
  state.snoozeMinutes = 0

  def anyOpenDev = getChildDevice("OpenDoorAlert_${app.id}")
  if(!anyOpenDev) anyOpenDev = addChildDevice("hubitat", "Virtual Contact Sensor", "OpenDoorAlert_${app.id}", null, [label: thisName, name: thisName])

  handleDoorEvent()  // need to check for doors open at start up also

  subscribe(contactSensors, "contact", handlerContact)
  subscribe(snoozeCount, "temperature", handlerTemp)
}

def areAnyOpen() {
  def anyOpen = false
  contactSensors.each {
    if(it.currentContact == "open") anyOpen = true
  }
  return anyOpen
}

def handlerContact(evt) {
  if (debugLog) log.debug "ODA: contact event $evt.device $evt.value"
  handleDoorEvent()
}

def handlerTemp(evt) {
  if (debugLog) log.debug "ODA: snooze (temp) event $evt.device $evt.value"
  timerHandler(true)
}

def timerHandler(fromInput) {
  //if (debugLog) log.debug "ODA: timer handler $fromInput"

  if (state.doorAlarmTime == null) state.doorAlarmTime = 0
  if (state.snoozeMinutes == null) state.snoozeMinutes = 0

  // Check if door alarm timer is active
  if (state.doorAlarmTime > 0 && (state.doorAlarmTime < now())) {
    doorAlarm()
  }

  if (state.snoozeMinutes <= 0) {
    if (snoozeCount.currentTemperature.toInteger() > 0) {
      state.snoozeMinutes = now() + (5 * 60 * 1000)
    }
  } else {
    if (snoozeCount.currentTemperature.toInteger() <= 0) {
      state.snoozeMinutes = 0
    } else if (state.snoozeMinutes < now() && fromInput != true) {
        state.snoozeMinutes = now() + (5 * 60 * 1000)
        if (snoozeCount.currentTemperature.toInteger() >= 5) {
          snoozeCount.setTemperature(snoozeCount.currentTemperature - 5)
        } else {
          snoozeCount.setTemperature(0)
        }
    }
  } 

  if ((state.snoozeMinutes > 0) || (state.doorAlarmTime > 0)) {
    if (debugLog) log.debug "ODA: timer set timeout snooze:${snoozeCount.currentTemperature.toInteger()} doorAlarm:${state.doorAlarmTime}"
    //runIn(60 * 5, timerHandler, [overwrite: true, data: [fromInput: false]])
    runIn(60 * 5, timerHandler)
  }
}

def handleDoorEvent() {
  def anyOpenDev = getChildDevice("OpenDoorAlert_${app.id}")
  if(areAnyOpen() == true) {
    // show that at least one of the doors is open in the virtual device
    anyOpenDev.open()

    // if any doors open start a timer for minutesToAlertDay unless already running
    if (state.doorAlarmTime == 0) {
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
      state.doorAlarmTime = now() + minutesToAlertDay * 60 * 1000
      runIn(60 * 5, timerHandler)
    }
  } else { 
    // show that no doors are open in the virtual device
    anyOpenDev.close()

    // if no doors open, set to zero to indicate no timer
    state.doorAlarmTime = 0
  }
}

def doorAlarm() {
  if (state.doorAlarmTime > 0) {
    state.doorAlarmTime = 0
    send(alertMessage)

    if (repeat) {
      if (debugLog) log.debug "ODA: repeat new timer for $minutesToRepeat"
      state.doorAlarmTime = now() + minutesToRepeat * 60 * 1000
    }
  }
}

private send(message) {
  if (!state.snoozeRunning) {
    alarmLights.each {
      it.on() 
    }

    if (sendPushMessage != null) {
      if (debugLog) log.debug("ODA: Send Notification: $message")
      sendPushMessage.deviceNotification(message)
    } 
  } else {
    if (debugLog) log.debug("ODA: Notification SKIPPED! $message")
  }
}
