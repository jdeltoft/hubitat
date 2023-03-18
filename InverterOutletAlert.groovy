definition(
  name: "Inverter Outlet Alert",
  namespace: "hubitat",
  author: "Justin Eltoft",
  description: "Alert if a virtual switch indicates the inverter outlet is off (GFCI or batter dead)",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "")

preferences {
  page(name: "mainPage")
}

// TODO:
// - 

def mainPage() {
  dynamicPage(name: "mainPage", title: "Inverter Outlet Alert (ver:0.10)", install: true, uninstall: true) {
    section {
      input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Inverter Outlet Alert (NameMe)"
      if(thisName) app.updateLabel("$thisName")

      input "contactSensor", "capability.contactSensor", title: "Select Contact Sensor", submitOnChange: true, required: true, multiple: false
      input "alarmLights", "capability.switch", title: "Select Alarm Lights", submitOnChange: true, required: false, multiple: true
      input "alertMessage", "text", title: "Alert Message", defaultValue: "Inverter Outlet Alert!", required: true 
      input "repeat", "bool", title: "repeat:", defaultValue: false, submitOnChange: true
      if (repeat) {
          input "minutesToRepeat", "number", title: "Repeat Alerts Time (minutes):", defaultValue: 30, submitOnChange: true
      }
      if(contactSensor) paragraph "Outlet currently off: ${isOutletOff()}"
    }

    section("Notifications") {
      input "sendPushMessage", "capability.notification", title: "Send a push notification?", multiple: true, required: false
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
  deleteChildDevice("InverterOutletAlert_${app.id}")
}

def initialize() {
  state.alarmTime = 0

  def outletDevice = getChildDevice("InverterOutletAlert_${app.id}")
  if(!outletDevice) outletDevice = addChildDevice("hubitat", "Virtual Contact Sensor", "InverterOutletAlert_${app.id}", null, [label: thisName, name: thisName])

  handleOutletEvent()  // need to check at start up also

  subscribe(contactSensor, "contact", handlerContact)
}

def isOutletOff() {
  return contactSensor.currentContact
}

def handlerContact(evt) {
  if (debugLog) log.debug "Inverter: contact event $evt.device $evt.value"
  handleOutletEvent()
}

def timerHandler(fromInput) {
  //if (debugLog) log.debug "Inverter: timer handler $fromInput"

  if (state.alarmTime == null) state.alarmTime = 0

  // Check if alarm timer is active
  if (state.alarmTime > 0 && (state.alarmTime < now())) {
    inverterOutletAlarm()
  }

  if (state.alarmTime > 0) {
    if (debugLog) log.debug "Inverter: timer set timeout alarm:${state.alarmTime}"
    runIn(60 * 5, timerHandler)
  }
}

def handleOutletEvent() {
  def outletDevice = getChildDevice("InverterOutletAlert_${app.id}")
  if(isOutletOff() == "closed") {
    // show that outlet is off in the virtual device
    outletDevice.open()

    // if outlet is off start a timer for minutesToAlertDay unless already running
    if (state.alarmTime == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "Inverter: sending alarm.."
      state.alarmTime = now()
      inverterOutletAlarm()
    }
  } else { 
    // show that outlet is on in the virtual device
    outletDevice.close()

    // if outlet is on, set to zero to indicate no timer
    state.alarmTime = 0
  }
}

def inverterOutletAlarm() {
  if (state.alarmTime > 0) {
    state.alarmTime = 0
    send(alertMessage)

    if (repeat) {
      log.info("Inverter: repeat new timer for $minutesToRepeat")
      state.alarmTime = now() + minutesToRepeat * 60 * 1000
    }
  }
}

private send(message) {
  alarmLights.each {
    it.on() 
  }

  if (sendPushMessage != null) {
    log.info("Inverter: ===> Send Notification: $message")
    sendPushMessage.deviceNotification(message)
  } 
}
